package example.streaming.config.mvc;

import java.util.List;
import java.util.concurrent.Future;

public interface FutureContainer {

    void collectFutures(List<Future<?>> sink);

}
