package example.streaming;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import org.springframework.ui.Model;

public interface AsyncModel extends Model {

    <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> attributeValue);


    interface AsyncValue<T> {
        T await() throws Exception;
    }


    @Override
    AsyncModel addAttribute(String attributeName, Object attributeValue);
    @Override
    AsyncModel addAttribute(Object attributeValue);
    @Override
    AsyncModel addAllAttributes(Collection<?> attributeValues);
    @Override
    AsyncModel addAllAttributes(Map<String, ?> attributes);
    @Override
    AsyncModel mergeAttributes(Map<String, ?> attributes);
}

