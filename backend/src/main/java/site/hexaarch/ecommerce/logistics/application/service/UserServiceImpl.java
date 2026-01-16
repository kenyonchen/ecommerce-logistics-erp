package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.service.UserService;

import java.util.List;

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
    public User createUser(String tenantId, String username, String password, String email, String phone, String firstName, String lastName) {
        // 加密密码
        String encodedPassword = passwordEncoder.encode(password);
        User user = User.create(tenantId, username, encodedPassword, email, phone, firstName, lastName);
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

    @Override
    public User updateUser(String id, String email, String phone, String firstName, String lastName) {
        User user = getUserById(id);
        user.updateInfo(email, phone, firstName, lastName);
        return userRepository.save(user);
    }

    @Override
    public User updatePassword(String id, String password) {
        User user = getUserById(id);
        String encodedPassword = passwordEncoder.encode(password);
        user.updatePassword(encodedPassword);
        return userRepository.save(user);
    }

    @Override
    public User activateUser(String id) {
        User user = getUserById(id);
        user.activate();
        return userRepository.save(user);
    }

    @Override
    public User deactivateUser(String id) {
        User user = getUserById(id);
        user.deactivate();
        return userRepository.save(user);
    }

    @Override
    public User lockUser(String id) {
        User user = getUserById(id);
        user.lock();
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    @Override
    public User assignRoleToUser(String userId, String roleId) {
        User user = getUserById(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.addRole(role);
        return userRepository.save(user);
    }

    @Override
    public User removeRoleFromUser(String userId, String roleId) {
        User user = getUserById(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.removeRole(role);
        return userRepository.save(user);
    }
}