package innexo;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class UserService {
  @Autowired private JdbcTemplate jdbcTemplate;

  static final int ADMINISTRATOR = 0;
  static final int TEACHER = 1;
  static final int STUDENT = 2;

  public User getById(int id) {
    String sql = "SELECT id, name, password_hash, permission_level FROM user WHERE id=?";
    RowMapper<User> rowMapper = new UserRowMapper();
    User user = jdbcTemplate.queryForObject(sql, rowMapper, id);
    return user;
  }

  public List<User> getByName(String name) {
    String sql =
        "SELECT id, name, password_hash, permission_level FROM user WHERE name=?";
    RowMapper<User> rowMapper = new UserRowMapper();
    List<User> users = jdbcTemplate.query(sql, rowMapper, name);
    return users;
  }

  public List<User> getAll() {
    String sql = "SELECT id, name, password_hash, permission_level FROM user";
    RowMapper<User> rowMapper = new UserRowMapper();
    return this.jdbcTemplate.query(sql, rowMapper);
  }

  public void add(User user) {
    // Add user
    String sql =
        "INSERT INTO user (id, name, password_hash, permission_level) values (?, ?, ?, ?)";
    jdbcTemplate.update(
        sql, user.id, user.name, user.passwordHash, user.permissionLevel);

    // Fetch user id
    sql =
        "SELECT id FROM user WHERE name=? AND password_hash=? AND permission_level=?";
    int id =
        jdbcTemplate.queryForObject(
            sql, Integer.class, user.name, user.passwordHash, user.permissionLevel);

    // Set user id
    user.id = id;
  }

  public void addManager(int userId, int managerId) {
    String sql =
        "INSERT INTO user_relationship (manager_id, managed_id) VALUES (?, ?)";
    jdbcTemplate.update(sql, managerId, userId);
  }

  public void removeManager(int userId, int managerId) {
    String sql =
        "DELETE FROM user_relationship WHERE manager_id=? AND managed_id=?";
    jdbcTemplate.update(sql, managerId, userId);
  }

  public void update(User user) {
    String sql =
        "UPDATE user SET id=?, name=?, password_hash=?, permission_level=? WHERE id=?";
    jdbcTemplate.update(
        sql, user.id, user.name, user.passwordHash, user.permissionLevel, user.id);
  }

  public User delete(int id) {
    User user = getById(id);
    String deleteRelationshipSql =
        "DELETE FROM user_relationship WHERE manager_id=? OR managed_id=?";
    jdbcTemplate.update(deleteRelationshipSql, id, id);
    String sql = "DELETE FROM user WHERE id=?";
    jdbcTemplate.update(sql, id);
    return user;
  }

  public boolean exists(int id) {
    String sql = "SELECT count(*) FROM user WHERE id=?";
    int count = jdbcTemplate.queryForObject(sql, Integer.class, id);
    return count != 0;
  }

  public List<User> managers(int id) {
    String sql =
        "SELECT u.id, u.name, u.password_hash, u.permission_level FROM user_relationship r LEFT JOIN user u ON r.manager_id = u.id WHERE r.managed_id=?";
    RowMapper<User> rowMapper = new UserRowMapper();
    List<User> users = jdbcTemplate.query(sql, rowMapper, id);
    return users;
  }

  public List<User> managedBy(int id) {
    String sql =
        "SELECT u.id, u.name, u.password_hash, u.administrator, u.permission_level FROM user_relationship r LEFT JOIN user u ON r.managed_id = u.id WHERE r.manager_id=?";
    RowMapper<User> rowMapper = new UserRowMapper();
    List<User> users = jdbcTemplate.query(sql, rowMapper, id);
    return users;
  }
}
