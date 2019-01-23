package me.sml.demooauth.controller;

import lombok.extern.slf4j.Slf4j;
import me.sml.demooauth.config.SessionConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
public class MainController {

    private HttpSession httpSession;

    public MainController(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    @GetMapping("/me")
    public Map<String, Object> me(){
        log.info("Session : {}", String.valueOf(httpSession.getAttribute(SessionConstants.LOGIN_USER)));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", httpSession.getAttribute("LOGIN_USER"));
        return response;
    }
}
