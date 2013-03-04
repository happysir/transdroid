package org.transdroid.lite.app.settings;

import org.transdroid.lite.gui.navigation.FilterItem;

import android.net.Uri;
import android.text.TextUtils;

/**
 * Represents a user-specified website that can be searched (by starting the browser, rather than in-app)
 * @author Eric Kok
 */
public class WebsearchSetting implements FilterItem {

	private static final String DEFAULT_NAME = "Default";
	private static final String KEY_PREFIX = "websearch_";
	
	private final int order;
	private final String name;
	private final String baseUrl;

	public WebsearchSetting(int order, String name, String baseUrl) {
		this.order = order;
		this.name = name;
		this.baseUrl = baseUrl;
	}

	public int getOrder() {
		return order;
	}

	@Override
	public String getName() {
		if (!TextUtils.isEmpty(name))
			return name;
		if (!TextUtils.isEmpty(baseUrl))
			return Uri.parse(baseUrl).getHost();
		return DEFAULT_NAME;
	}
	
	public String getBaseUrl() {
		return baseUrl;
	}

	public String getKey() {
		return KEY_PREFIX + getOrder();
	}

}
