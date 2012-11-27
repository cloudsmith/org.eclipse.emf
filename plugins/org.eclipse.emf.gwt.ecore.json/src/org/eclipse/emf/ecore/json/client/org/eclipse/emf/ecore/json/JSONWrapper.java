/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.json;

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
class JSONWrapper {
	static class JArray extends JValue {
		JArray(JSONArray wrapped) {
			super(wrapped);
		}

		void add(JValue value) {
			((JSONArray)wrapped).set(((JSONArray)wrapped).size(), value.wrapped);
		}

		JValue get(int idx) {
			return convert(((JSONArray)wrapped).get(idx));
		}

		int size() {
			return ((JSONArray)wrapped).size();
		}
	}

	static class JBoolean extends JValue {
		JBoolean(JSONBoolean wrapped) {
			super(wrapped);
		}

		boolean booleanValue() {
			return ((JSONBoolean)wrapped).booleanValue();
		}
	}

	static class JNull extends JValue {
		JNull() {
			super(JSONNull.getInstance());
		}
	}

	static class JNumber extends JValue {
		JNumber(JSONNumber wrapped) {
			super(wrapped);
		}

		double doubleValue() {
			return ((JSONNumber)wrapped).doubleValue();
		}
	}

	static class JObject extends JValue {
		JObject(JSONObject wrapped) {
			super(wrapped);
		}

		JValue get(String key) {
			return convert(((JSONObject)wrapped).get(key));
		}

		void put(String key, JValue value) {
			((JSONObject)wrapped).put(key, value.wrapped);
		}
	}

	static class JString extends JValue {
		JString(JSONString wrapped) {
			super(wrapped);
		}

		String stringValue() {
			return ((JSONString)wrapped).stringValue();
		}
	}

	static abstract class JValue {
		protected final JSONValue wrapped;

		JValue(JSONValue wrapped) {
			this.wrapped = wrapped;
		}
	}

	private static final JNull JSON_NULL = new JNull();

	private static final JBoolean JSON_TRUE = new JBoolean(JSONBoolean.getInstance(true));

	private static final JBoolean JSON_FALSE = new JBoolean(JSONBoolean.getInstance(false));

	static JValue convert(JSONValue value) {
		if(value instanceof JSONString)
			return new JString((JSONString) value);
		if(value instanceof JSONNumber)
			return new JNumber((JSONNumber) value);
		if(value instanceof JSONBoolean)
			return ((JSONBoolean) value).booleanValue()
					? JSON_TRUE
					: JSON_FALSE;
		if(value instanceof JSONArray)
			return new JArray((JSONArray) value);
		if(value instanceof JSONObject)
			return new JObject((JSONObject) value);
		return JSON_NULL;
	}

	JArray createArray() {
		return new JArray(new JSONArray());
	}

	JBoolean createBoolean(boolean booleanValue) {
		return booleanValue
				? JSON_TRUE
				: JSON_FALSE;
	}

	JNull createNull() {
		return JSON_NULL;
	}

	JNumber createNumber(double numberValue) {
		return new JNumber(new JSONNumber(numberValue));
	}

	JObject createObject() {
		return new JObject(new JSONObject());
	}

	JString createString(String stringValue) {
		return new JString(new JSONString(stringValue));
	}

	JValue parse(String jsonString) {
		return convert(JSONParser.parseStrict(jsonString));
	}

	String serialize(JValue value) {
		return value.wrapped.toString();
	}
}
