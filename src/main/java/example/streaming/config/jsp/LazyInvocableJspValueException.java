package example.streaming.config.jsp;

import javax.el.ELException;

public class LazyInvocableJspValueException extends ELException {

    public LazyInvocableJspValueException(Throwable e) {
        super(e);
    }

}
