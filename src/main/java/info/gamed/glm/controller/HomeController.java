package info.gamed.glm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Home controller to make React URLs working.
 * @author Z@
 */
@Controller
public class HomeController {

    @RequestMapping(value = {"/", "/howtoplay", "/gamehub", "/profile", "/contact"})
    public String index() {
        return "index";
    }

}
