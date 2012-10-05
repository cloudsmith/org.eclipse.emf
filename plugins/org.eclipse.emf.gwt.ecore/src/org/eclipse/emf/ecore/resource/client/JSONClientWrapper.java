/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.resource.client;

import org.eclipse.emf.ecore.resource.JSONWrapper;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNull;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

/**
 * @author thhal
 * 
 */
public class JSONClientWrapper implements JSONWrapper {
	static class JClientArray extends JClientValue<JSONArray> implements JArray {
		JClientArray(JSONArray wrapped) {
			super(wrapped);
		}

		@Override
		public void add(JValue value) {
			wrapped.set(wrapped.size(), ((JClientValue<?>) value).wrapped);
		}

		@Override
		public JValue get(int idx) {
			return convert(wrapped.get(idx));
		}

		@Override
		public int size() {
			return wrapped.size();
		}
	}

	static class JClientBoolean extends JClientValue<JSONBoolean> implements JBoolean {
		JClientBoolean(JSONBoolean wrapped) {
			super(wrapped);
		}

		@Override
		public boolean booleanValue() {
			return wrapped.booleanValue();
		}
	}

	static class JClientNull extends JClientValue<JSONNull> implements JNull {
		JClientNull() {
			super(JSONNull.getInstance());
		}
	}

	static class JClientNumber extends JClientValue<JSONNumber> implements JNumber {
		JClientNumber(JSONNumber wrapped) {
			super(wrapped);
		}

		@Override
		public double doubleValue() {
			return wrapped.doubleValue();
		}
	}

	static class JClientObject extends JClientValue<JSONObject> implements JObject {
		JClientObject(JSONObject wrapped) {
			super(wrapped);
		}

		@Override
		public JValue get(String key) {
			return convert(wrapped.get(key));
		}

		@Override
		public void put(String key, JValue value) {
			wrapped.put(key, ((JClientValue<?>) value).wrapped);
		}
	}

	static class JClientString extends JClientValue<JSONString> implements JString {
		JClientString(JSONString wrapped) {
			super(wrapped);
		}

		@Override
		public String stringValue() {
			return wrapped.stringValue();
		}
	}

	static abstract class JClientValue<T extends JSONValue> implements JValue {
		protected final T wrapped;

		JClientValue(T wrapped) {
			this.wrapped = wrapped;
		}
	}

	private static final JClientNull JSON_NULL = new JClientNull();

	private static final JClientBoolean JSON_TRUE = new JClientBoolean(JSONBoolean.getInstance(true));

	private static final JClientBoolean JSON_FALSE = new JClientBoolean(JSONBoolean.getInstance(false));

	static JValue convert(JSONValue value) {
		if(value instanceof JSONString)
			return new JClientString((JSONString) value);
		if(value instanceof JSONNumber)
			return new JClientNumber((JSONNumber) value);
		if(value instanceof JSONBoolean)
			return ((JSONBoolean) value).booleanValue()
					? JSON_TRUE
					: JSON_FALSE;
		if(value instanceof JSONArray)
			return new JClientArray((JSONArray) value);
		if(value instanceof JSONObject)
			return new JClientObject((JSONObject) value);
		return JSON_NULL;
	}

	@Override
	public JArray createArray() {
		return new JClientArray(new JSONArray());
	}

	@Override
	public JBoolean createBoolean(boolean booleanValue) {
		return booleanValue
				? JSON_TRUE
				: JSON_FALSE;
	}

	@Override
	public JNull createNull() {
		return JSON_NULL;
	}

	@Override
	public JNumber createNumber(double numberValue) {
		return new JClientNumber(new JSONNumber(numberValue));
	}

	@Override
	public JObject createObject() {
		return new JClientObject(new JSONObject());
	}

	@Override
	public JString createString(String stringValue) {
		return new JClientString(new JSONString(stringValue));
	}

	@Override
	public JValue parse(String jsonString) {
		return convert(JSONParser.parseStrict(jsonString));
	}

	@Override
	public String serialize(JValue value) {
		return ((JClientValue<?>) value).wrapped.toString();
	}
}
