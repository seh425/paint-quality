package com.example.paintquality.excel;

import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.user.InspectorUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rounds")
public class RoundsController {

    private final TemplateService templateService;

    public RoundsController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<RoundInfo> rounds(@CurrentUser InspectorUser user) {
        return templateService.rounds(user.getCheckArea());
    }
}
