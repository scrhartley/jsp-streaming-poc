package example.streaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import example.streaming.service.BlockingSlowService;
import example.streaming.util.future.LazyTask;

@Controller
public class ExtrasController {
    @Autowired
    private BlockingSlowService service;

    @GetMapping("/atoms")
    public String atoms(Model model) {
        model.addAttribute("myData", new LazyTask<>(service::getData1));

        model.addAttribute("myData2", new LazyTask<>(() -> {
            String param = service.getIntermediateData();
            return service.getData2(param + " and other work done");
        }));

        return "extras/atoms";
    }

    @GetMapping("/error-boundaries")
    public String errorBoundaries(Model model) {
        for (int i = 1; i <= 4; i++) {
            model.addAttribute("throwsException" + i, new LazyTask<>(() -> {
                Thread.sleep(600);
                throw new Exception("Catch me!!!");
            }));
        }
        return "extras/error_boundaries";
    }

    @GetMapping("/suspend")
    public String suspend(Model model) {
        for (int i = 1; i <= 4; i++) {
            model.addAttribute("myData" + i, new LazyTask<>( () -> {
                Thread.sleep(2_500);
                return "Work done";
            }));
        }
        return "extras/suspend";
    }

}
