package pkqb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("pkqb.mapper")
public class PkqbApplication {

    public static void main(String[] args) {
        SpringApplication.run(PkqbApplication.class, args);
    }
}
