package site.hexaarch.ecommerce.logistics.application.service.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.UserService;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现
 *
 * @author kenyon
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User createUser(String tenantId, String username, String password, String email, String phone, String firstName, String lastName) {
        // users.email 列 NOT NULL+UNIQUE：邮箱选填时用用户名拼占位地址（含用户名，天然唯一），避免留空 500
        String effectiveEmail = (email == null || email.isBlank())
                ? username.trim() + "@placeholder.local"
                : email.trim();
        // 加密密码
        String encodedPassword = passwordEncoder.encode(password);
        User user = User.create(tenantId, username, encodedPassword, effectiveEmail, phone, firstName, lastName);
        return userRepository.save(user);
    }

    @Override
    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public List<User> getUsersByTenantId(String tenantId) {
        return userRepository.findByTenantId(tenantId);
    }

    /**
     * 全平台用户列表：门槛在控制器（{@code UserController#getUsers} 只在平台管理员留空 tenantId 时调用），
     * 租户用户走的仍是按租户查询分支。
     */
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(String id, String email, String phone, String firstName, String lastName) {
        User user = getUserById(id);
        user.updateInfo(email, phone, firstName, lastName);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updatePassword(String id, String password) {
        User user = getUserById(id);
        String encodedPassword = passwordEncoder.encode(password);
        user.updatePassword(encodedPassword);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User activateUser(String id) {
        User user = getUserById(id);
        user.activate();
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User deactivateUser(String id) {
        User user = getUserById(id);
        user.deactivate();
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User lockUser(String id) {
        User user = getUserById(id);
        user.lock();
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public User assignRoleToUser(String userId, String roleId) {
        User user = getUserById(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.addRole(role);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User removeRoleFromUser(String userId, String roleId) {
        User user = getUserById(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.removeRole(role);
        return userRepository.save(user);
    }
}