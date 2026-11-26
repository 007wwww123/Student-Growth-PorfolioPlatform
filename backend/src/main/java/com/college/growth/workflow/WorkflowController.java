package com.college.growth.workflow;

import com.college.growth.common.result.Result;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {
    private final WorkflowService workflow;

    public WorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/{studentId}")
    public Result<List<WorkflowService.Entry>> list(@PathVariable long studentId, @RequestParam String academicYear) {
        return workflow.list(studentId, academicYear);
    }

    @PostMapping("/{studentId}/{code}/{action:approve|return|withdraw}")
    public Result<Void> transition(@PathVariable long studentId, @PathVariable String code,
            @PathVariable String action, @RequestParam String academicYear,
            @RequestBody(required = false) WorkflowService.Decision decision) {
        return workflow.transition(studentId, code, action, academicYear, decision);
    }

    @PostMapping("/{studentId}/archive")
    public Result<Void> archive(@PathVariable long studentId, @RequestParam String academicYear,
            @RequestBody Map<String, Long> versions) {
        return workflow.archive(studentId, academicYear, versions);
    }
}
