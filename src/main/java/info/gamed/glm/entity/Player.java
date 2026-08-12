package info.gamed.glm.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * JPA entity for the "player" (user) table. Players are used for authentication and are not exposed as
 * a public REST collection; the current player is served by PlayerController (/api/player/me).
 *
 * @author Z@
 */
@Entity
@Table(
    name = "player"
)
public class Player {
    
    public static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "registered_date", nullable = false, unique = false)
    private Date registeredDate;
    
    @Column(name = "nick_name", nullable = false, length = 255, unique = false) 
    private String nickName;
    
    @Column(name = "login_name", nullable = false, length = 64, unique = true) 
    private String loginName;
    
    /**
     * Temporary using password. In future, will use Google authentication.
     * It must be called password and method setPassword() otherwise it does not work.
     */
    @Column(name = "password", nullable = false, length = 128, unique = false)
    private @JsonIgnore String password;
    
    // Roles are stored in a normalized side table (player_roles) rather than a raw array column, the
    // conventional JPA mapping for a collection of simple values. EAGER so they are available during
    // authentication (loadUserByUsername runs outside an open session/transaction).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_roles", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "role")
    private List<String> roles = new ArrayList<>();

    protected Player() {
    }

    public Player(Date registeredDate, String nickName, String loginName, String pass, String... roles) {
        this.registeredDate = registeredDate;
        this.nickName = nickName;
        this.loginName = loginName;
        this.setPassword(pass);
        this.roles = new ArrayList<>(Arrays.asList(roles));
    }

    @Override
    public String toString() {
        return String.format("Player[id=%d, registered_date='%s', nick_name='%s']", id, registeredDate, nickName);
    }

    public Long getId() {
        return id;
    }

    public Date getRegisteredDate() {
        return registeredDate;
    }

    public void setRegisteredDate(Date registeredDate) {
        this.registeredDate = registeredDate;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }
    
    public void setPassword(String password) {
        this.password = PASSWORD_ENCODER.encode(password);
    }

    public String getPassword() {
        return password;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, loginName, password, roles);
    }
}