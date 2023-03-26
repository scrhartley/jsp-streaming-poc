package simon.example.streaming.jsp;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.jsp.JspFactory;

@WebListener
public class ELResolverWebListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        JspFactory.getDefaultFactory()
                .getJspApplicationContext(event.getServletContext())
                .addELResolver(new FutureELResolver());
    }

}
