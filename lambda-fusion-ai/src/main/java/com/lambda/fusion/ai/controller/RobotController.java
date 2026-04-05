package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.CreateRobot;
import com.lambda.fusion.ai.model.Robot;
import com.lambda.fusion.ai.model.UpdateRobot;
import com.lambda.fusion.ai.service.RobotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai/robots")
@Tag(name = "AI 机器人管理")
@RequiredArgsConstructor
public class RobotController {

    private final RobotService robotService;

    @PostMapping
    @Operation(summary = "创建新的AI机器人")
    public Robot createRobot(@Valid @RequestBody CreateRobot dto) {
        return robotService.createRobot(dto);
    }

    @PutMapping
    @Operation(summary = "更新AI机器人")
    public Robot updateRobot(@Valid @RequestBody UpdateRobot dto) {
        return robotService.updateRobot(dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取AI机器人详情")
    public Robot getRobot(@PathVariable String id) {
        return robotService.getRobotById(id);
    }

    @GetMapping
    @Operation(summary = "获取所有AI机器人列表")
    public List<Robot> listRobots() {
        return robotService.listAllRobots();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除AI机器人")
    public Boolean deleteRobot(@PathVariable String id) {
        robotService.deleteRobot(id);
        return true;
    }
}
