package org.nlpcn.es4sql.domain;

import org.elasticsearch.common.netty.util.internal.StringUtil;

public class Index {
	private String index;
	private String type;

	public Index(String str) {
		String[] split = str.split("/");
		index = split[0].trim();
		if (split.length == 2) {
			type = split[1].trim();
		}
	}

	public String getIndex() {
		return index ;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	
}
