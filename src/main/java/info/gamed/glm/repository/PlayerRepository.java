package info.gamed.glm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import info.gamed.glm.entity.Player;

/**
 * Standard Spring Data JPA repository for {@link Player}. Not exposed as a REST resource; players are
 * read via PlayerController and used for authentication.
 * @author Z@
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {
    // Custom queries can be added here if needed
    Player findByLoginName(String loginName);
}
