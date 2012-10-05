/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.resource.server;

import org.eclipse.emf.ecore.resource.JSONWrapper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * A class capable of creating wrappers that uses Google Gson. This wrapper is intended
 * for the server side.
 */
public class JSONServerWrapper implements JSONWrapper {
	static class JServerArray extends JServerValue<JsonArray> implements JArray {
		JServerArray(JsonArray wrapped) {
			super(wrapped);
		}

		@Override
		public void add(JValue value) {
			wrapped.add(((JServerValue<?>) value).wrapped);
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

	static class JServerBoolean extends JServerValue<JsonPrimitive> implements JBoolean {
		JServerBoolean(JsonPrimitive wrapped) {
			super(wrapped);
		}

		@Override
		public boolean booleanValue() {
			return wrapped.getAsBoolean();
		}
	}

	static class JServerNull extends JServerValue<JsonNull> implements JNull {
		JServerNull() {
			super(JsonNull.INSTANCE);
		}
	}

	static class JServerNumber extends JServerValue<JsonPrimitive> implements JNumber {
		JServerNumber(JsonPrimitive wrapped) {
			super(wrapped);
		}

		@Override
		public double doubleValue() {
			return wrapped.getAsDouble();
		}
	}

	static class JServerObject extends JServerValue<JsonObject> implements JObject {
		JServerObject(JsonObject wrapped) {
			super(wrapped);
		}

		@Override
		public JValue get(String key) {
			return convert(wrapped.get(key));
		}

		@Override
		public void put(String key, JValue value) {
			wrapped.add(key, ((JServerValue<?>) value).wrapped);
		}
	}

	static class JServerString extends JServerValue<JsonPrimitive> implements JString {
		JServerString(JsonPrimitive wrapped) {
			super(wrapped);
		}

		@Override
		public String stringValue() {
			return wrapped.getAsString();
		}
	}

	static abstract class JServerValue<T extends JsonElement> implements JValue {
		protected final T wrapped;

		JServerValue(T wrapped) {
			this.wrapped = wrapped;
		}
	}

	public static final JSONServerWrapper INSTANCE = new JSONServerWrapper();

	private static final JServerNull JSON_NULL = new JServerNull();

	private static final JServerBoolean JSON_TRUE = new JServerBoolean(new JsonPrimitive(true));

	private static final JServerBoolean JSON_FALSE = new JServerBoolean(new JsonPrimitive(false));

	private static final GsonBuilder gsonBuilder = new GsonBuilder();

	static JValue convert(JsonElement value) {
		if(value instanceof JsonPrimitive) {
			JsonPrimitive jp = (JsonPrimitive) value;
			if(jp.isString())
				return new JServerString(jp);
			if(jp.isNumber())
				return new JServerNumber(jp);
			if(jp.isBoolean())
				return jp.getAsBoolean()
						? JSON_TRUE
						: JSON_FALSE;
		}
		if(value instanceof JsonArray)
			return new JServerArray((JsonArray) value);
		if(value instanceof JsonObject)
			return new JServerObject((JsonObject) value);
		return JSON_NULL;
	}

	@Override
	public JArray createArray() {
		return new JServerArray(new JsonArray());
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
		return new JServerNumber(new JsonPrimitive(numberValue));
	}

	@Override
	public JObject createObject() {
		return new JServerObject(new JsonObject());
	}

	@Override
	public JString createString(String stringValue) {
		return new JServerString(new JsonPrimitive(stringValue));
	}

	@Override
	public JValue parse(String jsonString) {
		JsonParser parser = new JsonParser();
		return convert(parser.parse(jsonString));
	}

	@Override
	public String serialize(JValue value) {
		StringBuilder bld = new StringBuilder();
		gsonBuilder.create().toJson(((JServerValue<?>) value).wrapped, bld);
		return bld.toString();
	}
}
