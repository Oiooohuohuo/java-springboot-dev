package {{package}}.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/**
 * 品种推广状态。
 *
 * <p>落库值为 code(与数据库 tinyint 对应),展示值为 desc。</p>
 *
 * <p>注意:枚举常量名一旦上线就是数据库契约,改名需要数据订正流程。</p>
 *
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Getter
public enum PlantStatus {

    /** 停推。 */
    STOP(0, "停推"),

    /** 推广。 */
    PROMOTE(1, "推广");

    /** 落库值:与数据库 tinyint 一致。 */
    @EnumValue
    private final Integer code;

    /** 中文描述:直接给前端展示。 */
    @JsonValue
    private final String desc;

    /**
     * 构造推广状态。
     *
     * @param code 落库值
     * @param desc 中文描述
     */
    PlantStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按落库值反查枚举。
     *
     * @param code 落库值
     * @return 对应枚举,未匹配返回 null
     */
    public static PlantStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
