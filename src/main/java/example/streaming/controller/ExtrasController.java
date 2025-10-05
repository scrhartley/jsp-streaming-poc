package example.streaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import example.streaming.AsyncModel;
import example.streaming.service.BlockingSlowService;

@Controller
public class ExtrasController {
    @Autowired
    private BlockingSlowService service;

    @GetMapping("/atoms")
    public String atoms(AsyncModel model) {
        model.addAttribute("myData", service::getData1);

        model.addAttribute("myData2", () -> {
            String param = service.getIntermediateData();
            return service.getData2(param + " and other work done");
        });

        return "extras/atoms";
    }

    @GetMapping("/error-boundaries")
    public String errorBoundaries(AsyncModel model) {
        for (int i = 1; i <= 4; i++) {
            model.addAttribute("throwsException" + i, () -> {
                Thread.sleep(600);
                throw new Exception("Catch me!!!");
            });
        }
        return "extras/error_boundaries";
    }

    @GetMapping("/suspend")
    public String suspend(AsyncModel model) {
        for (int i = 1; i <= 4; i++) {
            model.addAttribute("myData" + i, () -> {
                Thread.sleep(2_500);
                return "Work done";
            });
        }
        return "extras/suspend";
    }

    @GetMapping("/deferred")
    public String deferred(AsyncModel model) {
        for (int i = 1; i <= 6; i++) {
            model.addAttribute("myData" + i, () -> {
                Thread.sleep(2_500);
                return "Work done";
            });
        }
        return "extras/deferred";
    }

}
