package pkqb.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Argon2idUtil {

    /*
     * 设置默认推荐参数
     */
    private static final int ITERATIONS = 2;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 1;

    //创建Argon2工厂类  推荐 argon2id类型共有三种类型可选（ARGON2i,ARGON2d,ARGON2id;）
    private static  final Argon2Factory.Argon2Types TYPE = Argon2Factory.Argon2Types.ARGON2id;

    //创建Argon2实例
    private static final Argon2 INSTANCE = Argon2Factory.create(TYPE);

    /**
     * 工具类禁止实例化
     */
    private Argon2idUtil(){
        throw new UnsupportedOperationException("工具类不能被实例化");
    }
    /**
     * 加密   Argon2 会自动生成随机盐值
     * @param password 密码
     * @return 加密后的密码
     */
    public static String hash(String password){
        //使用argon2id加密
        return INSTANCE.hash(ITERATIONS, MEMORY, PARALLELISM, password.toCharArray());
    }


    /**
     * 验证
     * @param encodedPassword 加密后的密码
     * @param password 密码
     * @return 验证结果
     */
    public static boolean verify(String encodedPassword,String password){
        return INSTANCE.verify(encodedPassword, password.toCharArray());
    }
}
