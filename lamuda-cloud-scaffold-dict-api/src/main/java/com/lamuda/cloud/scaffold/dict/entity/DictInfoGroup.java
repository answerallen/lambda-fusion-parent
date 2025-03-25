package com.lamuda.cloud.scaffold.dict.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 数据字典分组信息
 * @author Jin
 */
@Getter
@Setter
@Schema(description = "数据字典分组信息")
public class DictInfoGroup {
	/**
	 * 字典类型
	 */
	private String dictType;

	/**
	 *字典信息
	 */
	private List<DictInfo> dictList;
}
