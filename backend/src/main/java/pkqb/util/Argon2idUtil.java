package pkqb.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.extern.slf4j.Slf4j;

/**
 * Argon2id密码加密工具类
 * 使用Argon2id算法进行密码哈希和验证
 */
@Slf4j
public class Argon2idUtil {

    private static final int ITERATIONS = 2;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 1;

    private static  final Argon2Factory.Argon2Types TYPE = Argon2Factory.Argon2Types.ARGON2id;

    private static final Argon2 INSTANCE = Argon2Factory.create(TYPE);

    private Argon2idUtil(){
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 加密密码
     * Argon2会自动生成随机盐值
     *
     * @param password 明文密码
     * @return 加密后的密码哈希值
     */
    public static String hash(String password){
        return INSTANCE.hash(ITERATIONS, MEMORY, PARALLELISM, password.toCharArray());
    }


    /**
     * 验证密码
     *
     * @param encodedPassword 加密后的密码哈希值
     * @param password 明文密码
     * @return 验证结果，true表示密码正确
     */
    public static boolean verify(String encodedPassword,String password){
        return INSTANCE.verify(encodedPassword, password.toCharArray());
    }
}
