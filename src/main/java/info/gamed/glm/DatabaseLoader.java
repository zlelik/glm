package info.gamed.glm;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import info.gamed.glm.entity.Player;
import info.gamed.glm.repository.PlayerRepository;

/**
 * Seeds demo PLAYERS on startup for every profile EXCEPT prod ({@code @Profile("!prod")}), so a production
 * database is never populated with demo rows. Active for local dev, the dev profile and tests.
 *
 * Games and cells are NOT seeded: players create and join their own games through the UI.
 * @author Z@
 */
@Component
@Profile("!prod")
public class DatabaseLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseLoader.class);

    private final PlayerRepository playerRepository;

    public DatabaseLoader(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Seeds demo players only when the player table is empty, so the loader is idempotent and a restart
     * against a persistent database (ddl-auto=update) does not re-insert rows or hit the unique login_name
     * constraint.
     */
    @Override
    public void run(String... strings) throws Exception {
        if (this.playerRepository.count() == 0) {
            log.info("No players found - seeding demo players");
            Player player1 = new Player(new Date(), "Player1 ASD", "player1", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player1", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));

            Player player2 = new Player(new Date(), "Player2 Nick", "player2", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player2", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));
            Thread.sleep(1000);
            Player player3 = new Player(new Date(), "Player3 Super Name", "player3", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player3", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));

            Player player4 = new Player(new Date(), "Player4 ABC", "player4", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player4", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));

            Player player5 = new Player(new Date(), "Player5 DEF", "player5", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player5", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));

            Player player6 = new Player(new Date(), "Player6 XYZ", "player6", "1", "NORMAL_PLAYER");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player6", "1", AuthorityUtils.createAuthorityList("NORMAL_PLAYER")));

            this.playerRepository.save(player1);
            this.playerRepository.save(player2);
            this.playerRepository.save(player3);
            this.playerRepository.save(player4);
            this.playerRepository.save(player5);
            this.playerRepository.save(player6);
            SecurityContextHolder.clearContext();
        } else {
            log.info("Players already exist - skipping player seeding");
        }
    }
}
