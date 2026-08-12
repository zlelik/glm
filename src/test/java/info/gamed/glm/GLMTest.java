package info.gamed.glm;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;


/**
 * This is test class to test final application that it is working and does not have any JavaScript and other errors.
 *
 * The test boots the full Spring Boot application via {@link SpringBootTest} with a DEFINED_PORT web
 * environment, so the embedded server listens on port 8080 inside the test JVM. {@code mvn clean test}
 * is therefore self-contained: it starts the server and drives a real browser against
 * http://localhost:8080/ via Selenide.
 *
 * Usage examples:
 * - Run all tests: mvn clean test
 * - Run 1 specific test: mvn clean test -Dtest=GLMTest#specificNameTest
 * - Run tests with visible Chrome: mvn clean test -Dheadless=false
 * - Point at a specific Chrome/Chromium binary: mvn clean test -DchromeBinary=/usr/bin/chromium
 * - Run all tests including slow tests, which are skipped by default: mvn clean test -DspecificNameSkippedTest=true
 * - Run single specific slow test: mvn clean test -Dtest=GLMTest#specificNameSlowTest -DspecificNameSlowTest=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class GLMTest {
    static {
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "'['yyyy-MM-dd HH:mm:ss.SSS']'");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogLevel", "false");
    }
    private static final Logger logger = LoggerFactory.getLogger(GLMTest.class);
    private static int PAGE_LOAD_TIMEOUT = 60000;// Milliseconds
    private static int ELEMENT_STATE_TIMEOUT = 60000;// Milliseconds

    public static boolean headless = !("false".equals(System.getProperty("headless")));

    private static boolean MANUAL_TESTS_VERIFICATION = (headless == false);

    public static String MAIN_APP_URL = "http://localhost:8080/";
    
    /**
     * Common tests setup: Setup browser, Chrome options, timeout, etc. 
     */
    @BeforeAll
    static void setup() {
        logger.info("Test setup started.");
        ChromeOptions options = new ChromeOptions();

        logger.info("Test setup headless: " + headless);
        
        // these flags are needed to run WebGL in headless mode in Command Line or Docker, otherwise the game renders nothing and tests might fail.
        options.addArguments(
                "--enable-webgl",
                "--disable-web-security",
                "--allow-file-access-from-files",
                "--disable-site-isolation-trials",
                "--no-sandbox",
                "--disable-dev-shm-usage"
        );
        
        if (headless == true) {
            options.addArguments("--use-angle=swiftshader");// Use CPU to simulate GPU, needed for WebGL in headless mode in Command Line or Docker.
        }

        // Allow pointing the test at a specific Chrome/Chromium binary. On Linux CI/containers the browser
        // is usually Chromium at /usr/bin/chromium (which Selenium Manager does not always auto-detect),
        // while on a developer machine Chrome is found automatically. Override with -DchromeBinary=/path.
        String chromeBinary = System.getProperty("chromeBinary");
        if (chromeBinary == null && new File("/usr/bin/chromium").exists()) {
            chromeBinary = "/usr/bin/chromium";
        }
        if (chromeBinary != null) {
            logger.info("Using Chrome/Chromium binary: " + chromeBinary);
            options.setBinary(chromeBinary);
        }

        Configuration.browser = "chrome";
        Configuration.headless = headless;
        Configuration.pageLoadTimeout = PAGE_LOAD_TIMEOUT;
        Configuration.timeout = ELEMENT_STATE_TIMEOUT;
        Configuration.browserCapabilities = options;
        logger.info("Test setup finished.");
    }
    
    /**
     * Find the required HTML file and open it. 
     */
    @BeforeEach
    void setUp() {
        open(MAIN_APP_URL);
    }
    
    /**
     * Test for JavaScript errors.
     */
    @Test
    public void noJSErrorsHtmlOpenTest() {
        logger.info("Test [noJSErrorsHtmlOpenTest] started.");
        assertNoJSErrors();
        sleepForManualTest();
        logger.info("Test [noJSErrorsHtmlOpenTest] finished.");
    }

    /**
     * Logs in through the UI as player1 and verifies the navigation reflects the authenticated state:
     * the nav shows a Logout link with the player's nick name and a Game Hub menu, and no Login link.
     */
    @Test
    public void loginShowsLogoutAndGameHubTest() {
        logger.info("Test [loginShowsLogoutAndGameHubTest] started.");

        // Home page starts logged out: the nav shows a Login link.
        $("a[href='/login']").shouldBe(visible);

        // Go to the login form and sign in with the seeded demo credentials (see DatabaseLoader).
        $("a[href='/login']").click();
        $("#username").shouldBe(visible).setValue("player1");
        $("#password").setValue("1");
        $("button[type='submit']").click();

        // After a successful login Spring redirects back to "/", the React app re-mounts and fetches the
        // current player, so the Logout link (with the player's nick name) and the Game Hub menu appear.
        $("a[href='/logout']").shouldBe(visible).shouldHave(text("Logout")).shouldHave(text("Player1 ASD"));
        $("a[href='/gamehub']").shouldBe(visible).shouldHave(text("Game Hub"));
        // An authenticated user is not shown the Login link.
        $("a[href='/login']").shouldNot(exist);

        assertNoJSErrors();
        sleepForManualTest();
        logger.info("Test [loginShowsLogoutAndGameHubTest] finished.");
    }


    private BufferedImage getCanvasImage(String canvasSelector) {
        File screenshot = $(canvasSelector).getScreenshotAs(org.openqa.selenium.OutputType.FILE);
        try {
            return ImageIO.read(screenshot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read screenshot", e);
        }
    }

    private void assertNoJSErrors() {
        WebDriver driver = WebDriverRunner.getWebDriver();
        LogEntries logs = driver.manage().logs().get(LogType.BROWSER);

        boolean hasJavaScriptError = false;
        String errorText = "";
        for (LogEntry log : logs) {
            if (log.getLevel() == Level.SEVERE) {
                hasJavaScriptError = true;
                errorText = log.getMessage();
                break;
            }
        }
        assertFalse(hasJavaScriptError, String.format("There are JavaScript errors [%s] in the console .", errorText));
    }

    private void sleepForManualTest() {
        if (MANUAL_TESTS_VERIFICATION) {
            sleep(2000);
        }
    }
 
    /**
     * Run like this, otherwise press enter does not work.
     * mvn -DforkCount=0 test
     * Not used for now, but might be reconsidered in future.
     */
    @SuppressWarnings("unused")
    private void waitForEnterInput() {
        if (MANUAL_TESTS_VERIFICATION) {
            logger.info("Press enter to continue");
            try (Scanner scanner = new Scanner(System.in)) {
                scanner.nextLine();
            }
        }
    }

}