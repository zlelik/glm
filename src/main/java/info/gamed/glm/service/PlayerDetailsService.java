package info.gamed.glm.service;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import info.gamed.glm.entity.Player;
import info.gamed.glm.repository.PlayerRepository;

/**
 * This class is standard UserDetailsService required for Security Configuration.
 * @author Z@
 */
@Component
public class PlayerDetailsService implements UserDetailsService {

	private final PlayerRepository repository;

	public PlayerDetailsService(PlayerRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String loginName) throws UsernameNotFoundException {
		Player player = this.repository.findByLoginName(loginName);
		if (player == null) {
			// Contract of UserDetailsService: an unknown user must raise UsernameNotFoundException, not
			// a NullPointerException. Spring Security translates this into a clean authentication failure.
			throw new UsernameNotFoundException("No user found with login name: " + loginName);
		}
		return new User(player.getLoginName(), player.getPassword(),
				AuthorityUtils.createAuthorityList(player.getRoles().toArray(new String[0])));
	}

}
