package simon.example.streaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import simon.example.streaming.service.BlockingSlowService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Demonstrates using an ExecutorService directly in a Controller.
 * Uses an ExecutorService implementation that can be used in place of a normal concurrent one.
 * This ExecutorService avoids the rough edges of the alternative of using Callables directly
 * and allows a straightforward path for migrating to proper Futures at a later point in time.
 */
@Controller
@RequestMapping("/blocking-futures")
public class BlockingFuturesController {
    @Autowired
    private BlockingSlowService service;
    @Autowired
    private ExecutorService blockingExecutorService;

    @GetMapping("/basic")
    public String basicPage(Model model) {

        model.addAttribute("myData", blockingExecutorService.submit(service::getData1));

        model.addAttribute("myData2", blockingExecutorService.submit(() -> {
            String param = service.getIntermediateData();
            return service.getData2(param + " and other work done");
        }));

        return "blocking_futures";
    }

    @GetMapping("/dependencies")
    public String pageWithDependencies(Model model) {

        Future<String> data1 = blockingExecutorService.submit(service::getData1);

        model.addAttribute("myData", data1);

        model.addAttribute("myData2", blockingExecutorService.submit(() -> {
            String data = data1.get();
            return service.getData2(data + " and sub-work is done");
        }));

        return "blocking_futures";
    }

}
