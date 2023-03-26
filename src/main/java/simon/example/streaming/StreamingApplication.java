package simon.example.streaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
public class StreamingApplication{

	public static void main(String[] args) {
		SpringApplication.run(StreamingApplication.class, args);
	}

}
