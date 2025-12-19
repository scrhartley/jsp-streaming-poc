package example.streaming;

import java.util.concurrent.Callable;

import org.springframework.ui.Model;

public interface AsyncModel extends Model {

    <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> attributeValue);

    <T> void addUnordered(String attributeName, Callable<T>... callables);

    AsyncModel addSubModel(String attributeName); // Returns new sub-model


    interface AsyncValue<T> {
        T await() throws Exception;
    }

}

