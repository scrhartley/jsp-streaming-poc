package example.streaming;

import java.util.concurrent.Callable;

import org.springframework.ui.Model;

public interface AsyncModel extends Model {

    <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> attributeValue);


    interface AsyncValue<T> {
        T await() throws Exception;
    }
}

