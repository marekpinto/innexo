package innexo;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InnexoApiController {
  @Autowired UserService userService;
  @Autowired EncounterService encounterService;
  @Autowired LocationService locationService;
  @Autowired ApiKeyService apiKeyService;


  static final ResponseEntity<?> BAD_REQUEST = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  static final ResponseEntity<?> INTERNAL_SERVER_ERROR =
      new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  static final ResponseEntity<?> OK = new ResponseEntity<>(HttpStatus.OK);
  static final ResponseEntity<?> NOT_FOUND = new ResponseEntity<>(HttpStatus.NOT_FOUND);

  Integer parseInteger(String str) {
    return str == null ? null : Integer.parseInt(str);
  }

  Boolean parseBoolean(String str) {
    return str == null ? null : Boolean.parseBoolean(str);
  }

  Timestamp parseTimestamp(String str) {
    return str == null ? null : Utils.getTimestamp(parseInteger(str));
  }

  User fillUser(User user) {
    return user;
  }

  Location fillLocation(Location location) {
    return location;
  }

  ApiKey fillApiKey(ApiKey apiKey) {
    apiKey.user = fillUser(userService.getById(apiKey.userId));
    return apiKey;
  }

  Encounter fillEncounter(Encounter encounter) {
    encounter.location = fillLocation(locationService.getById(encounter.locationId));
    encounter.user = fillUser(userService.getById(encounter.userId));
    return encounter;
  }

  User getUserIfValid(String key) {
    String hash = Utils.encodeApiKey(key);
    if (apiKeyService.existsByKeyHash(hash)) {
      ApiKey apiKey = apiKeyService.getByKeyHash(hash);
      if (apiKey.expirationTime.getTime() > System.currentTimeMillis()) {
        if (userService.exists(apiKey.userId)) {
          return userService.getById(apiKey.userId);
        }
      }
    }
    return null;
  }

  boolean isAdministrator(String key) {
    User user = getUserIfValid(key);
    return user != null && user.permissionLevel == UserService.ADMINISTRATOR;
  }

  boolean isTrusted(String key) {
    User user = getUserIfValid(key);
    return user != null && user.permissionLevel <= UserService.TEACHER;
  }

  @RequestMapping("encounter/new/")
  public ResponseEntity<?> newEncounter(
      @RequestParam("userId") Integer userId,
      @RequestParam("locationId") Integer locationId,
      @RequestParam("type") String type,
      @RequestParam("apiKey") String apiKey) {
    if (!Utils.isBlank(type)
        && locationService.exists(locationId)
        && userService.exists(userId)
        && !Utils.isBlank(apiKey)
        && isTrusted(apiKey)) {
      Encounter encounter = new Encounter();
      encounter.locationId = locationId;
      encounter.userId = userId;
      encounter.time = new Timestamp(System.currentTimeMillis());
      encounter.type = type;
      encounterService.add(encounter);
      // return the filled encounter on success
      return new ResponseEntity<>(fillEncounter(encounter), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("user/new/")
  public ResponseEntity<?> newUser(
      @RequestParam("userId") Integer userId,
      @RequestParam("userName") String name,
      @RequestParam(value = "permissionLevel", defaultValue = "3") Integer permissionLevel,
      @RequestParam("password") String password,
      @RequestParam("apiKey") String apiKey) {
    if (!Utils.isBlank(name)
        && !Utils.isBlank(password)
        && !userService.exists(userId)
        && isAdministrator(apiKey)) {
      User u = new User();
      u.id = userId;
      u.name = name;
      u.passwordHash = Utils.encodePassword(password);
      u.permissionLevel = permissionLevel;
      userService.add(u);
      return new ResponseEntity<>(fillUser(u), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("location/new/")
  public ResponseEntity<?> newLocation(
      @RequestParam("locationName") String name,
      @RequestParam("locationTags") String tags,
      @RequestParam("apiKey") String apiKey) {
    if (!Utils.isBlank(name) && !Utils.isBlank(tags) && isAdministrator(apiKey)) {
      Location location = new Location();
      location.name = name;
      location.tags = tags;
      locationService.add(location);
      return new ResponseEntity<>(fillLocation(location), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("apiKey/new/")
  public ResponseEntity<?> newApiKey(
      @RequestParam(value = "userId", defaultValue = "-1") Integer userId,
      @RequestParam(value = "userName", defaultValue = "") String userName,
      @RequestParam("expirationTime") Integer expirationTime,
      @RequestParam("password") String password) {
    // if they gave a username instead of a userId
    if (userId == -1 && !Utils.isBlank(userName)) {
      // get list of users
      List<User> users = userService.getByName(userName);
      // if there's someone with the username
      if (users.size() > 0) {
        userId = users.get(0).id;
      }
    }

    // now actually make user
    if (userService.exists(userId)) {
      User u = userService.getById(userId);
      System.out.println("new ApiKey: user exists");
      if (Utils.matchesPassword(password, u.passwordHash)) {
        System.out.println("new ApiKey: password matches");
        ApiKey apiKey = new ApiKey();
        apiKey.userId = userId;
        apiKey.creationTime = new Timestamp(System.currentTimeMillis());
        apiKey.expirationTime = Utils.getTimestamp(expirationTime);
        apiKey.key = UUID.randomUUID().toString(); // quick hacks, please replace
        apiKey.keyHash = Utils.encodeApiKey(apiKey.key);
        System.out.println("new ApiKey: key <" + apiKey.keyHash + "> generated for user " + userId);
        apiKeyService.add(apiKey);
        return new ResponseEntity<>(fillApiKey(apiKey), HttpStatus.OK);
      }
      System.out.println("new ApiKey: password does not match");
    } else {
      System.out.println("new ApiKey: userId " + userId + " invalid");
    }
    return BAD_REQUEST;
  }

  @RequestMapping("user/update/")
  public ResponseEntity<?> updateUser(@RequestParam Map<String, String> allRequestParam) {
    if (allRequestParam.containsKey("apiKey")
        && isAdministrator(allRequestParam.get("apiKey")) // make sure changer is admin
        && allRequestParam.containsKey("userId") // make sure user exists
        && userService.exists(parseInteger(allRequestParam.get("userId")))
        && (allRequestParam.containsKey("userName")
            ? !Utils.isBlank(allRequestParam.get("userName"))
            : true) // if they are trying to set a name, it cannot be blank
        && (allRequestParam.containsKey("password")
            ? !Utils.isBlank(allRequestParam.get("password"))
            : true) // if they are trying to set a password, it cannot be blank
    ) {
      User user = userService.getById(parseInteger(allRequestParam.get("userId")));

      // if it is specified, set the name
      if(allRequestParam.containsKey("userName")) {
        user.name = allRequestParam.get("userName");
      }
      // if it is specified, set the password
      if(allRequestParam.containsKey("password")) {
        user.passwordHash = Utils.encodePassword(allRequestParam.get("password"));
      }

      // if it is specified, set the perm level
      if(allRequestParam.containsKey("permissionLevel")) {
        user.permissionLevel = parseInteger(allRequestParam.get("permissionLevel"));
      }

      userService.update(user);
      return new ResponseEntity<>(fillUser(user), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  // This method updates the password for same user only
  @RequestMapping("user/updatePassword")
  public ResponseEntity<?> updatePassword(@RequestParam("userId") Integer userId,
      @RequestParam("oldPassword") String oldPassword,
      @RequestParam("newPassword") String newPassword) {
      if( !Utils.isBlank(oldPassword) &&
          !Utils.isBlank(newPassword) &&
          userService.exists(userId) &&
          Utils.matchesPassword(oldPassword, userService.getById(userId).passwordHash))
      {
        User user = userService.getById(userId);
        user.passwordHash = Utils.encodePassword(newPassword);
        userService.update(user);
        return new ResponseEntity<>(fillUser(user), HttpStatus.OK);
      } else {
        return BAD_REQUEST;
      }
  }

  @RequestMapping("encounter/delete/")
  public ResponseEntity<?> deleteEncounter(
      @RequestParam("encounterId") Integer encounterId, @RequestParam("apiKey") String apiKey) {
    if (isAdministrator(apiKey)) {
      return new ResponseEntity<>(
          fillEncounter(encounterService.delete(encounterId)), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("user/delete/")
  public ResponseEntity<?> deleteUser(
      @RequestParam("userId") Integer userId, @RequestParam("apiKey") String apiKey) {
    if (isAdministrator(apiKey)) {
      return new ResponseEntity<>(fillUser(userService.delete(userId)), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("location/delete/")
  public ResponseEntity<?> deleteLocation(
      @RequestParam("locationId") Integer locationId, @RequestParam("apiKey") String apiKey) {
    if (isAdministrator(apiKey)) {
      return new ResponseEntity<>(fillLocation(locationService.delete(locationId)), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("apiKey/delete/")
  public ResponseEntity<?> deleteApiKey(
      @RequestParam("apiKeyId") Integer apiKeyId, @RequestParam("apiKey") String apiKey) {
    if (isAdministrator(apiKey)) {
      return new ResponseEntity<>(fillApiKey(apiKeyService.delete(apiKeyId)), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("encounter/")
  public ResponseEntity<?> viewEncounter(@RequestParam Map<String, String> allRequestParam) {
    String apiKey = allRequestParam.get("apiKey");
    if (!Utils.isBlank(apiKey) && isTrusted(apiKey)) {
      List<Encounter> els =
          encounterService
              .query(
                  parseInteger(allRequestParam.get("count")),
                  parseInteger(allRequestParam.get("encounterId")),
                  parseInteger(allRequestParam.get("userId")),
                  parseInteger(allRequestParam.get("locationId")),
                  parseTimestamp(allRequestParam.get("minTime")),
                  parseTimestamp(allRequestParam.get("maxTime")),
                  allRequestParam.get("userName"),
                  allRequestParam.get("type"))
              .stream()
              .map(x -> fillEncounter(x))
              .collect(Collectors.toList());
      return new ResponseEntity<>(els, HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("user/")
  public ResponseEntity<?> viewStudent(@RequestParam Map<String, String> allRequestParam) {
    if (allRequestParam.containsKey("apiKey") && isTrusted(allRequestParam.get("apiKey"))) {
      if (allRequestParam.containsKey("userId")) {
        return new ResponseEntity<>(
            Arrays.asList(userService.getById(Integer.parseInt(allRequestParam.get("userId")))),
            HttpStatus.OK);
      } else if (allRequestParam.containsKey("name")) {
        return new ResponseEntity<>(
            userService.getByName(allRequestParam.get("name")), HttpStatus.OK);
      } else {
        return new ResponseEntity<>(userService.getAll(), HttpStatus.OK);
      }
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("location/")
  public ResponseEntity<?> viewLocation(@RequestParam Map<String, String> allRequestParam) {
    if (allRequestParam.containsKey("apiKey") && isTrusted(allRequestParam.get("apiKey"))) {
      Stream<Location> stream = null;
      if (allRequestParam.containsKey("locationId")) {
        stream =
            Stream.of(locationService.getById(Integer.parseInt(allRequestParam.get("locationId"))));
      } else {
        stream = locationService.getAll().stream();
      }

      return new ResponseEntity<>(
          stream.map(x -> fillLocation(x)).collect(Collectors.toList()), HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("apiKey/")
  public ResponseEntity<?> viewApiKey(@RequestParam Map<String, String> allRequestParam) {
    if (allRequestParam.containsKey("apiKey") && isTrusted(allRequestParam.get("apiKey"))) {
      List<ApiKey> list =
          apiKeyService
              .query(
                  parseInteger(allRequestParam.get("apiKeyId")),
                  parseInteger(allRequestParam.get("userId")),
                  parseTimestamp(allRequestParam.get("minCreationTime")),
                  parseTimestamp(allRequestParam.get("maxCreationTime")),
                  Utils.encodeApiKey(allRequestParam.get("apiKeyData")))
              .stream()
              .map(x -> fillApiKey(x))
              .collect(Collectors.toList());
      return new ResponseEntity<>(list, HttpStatus.OK);
    } else {
      return BAD_REQUEST;
    }
  }

  @RequestMapping("validateTrusted/")
  public ResponseEntity<?> validateTrusted(@RequestParam("apiKey") String apiKey) {
    return isTrusted(apiKey) ? OK : BAD_REQUEST;
  }
}
