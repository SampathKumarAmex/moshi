/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import static java.util.Collections.unmodifiableList;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

/**
 * Reads a JSON (<a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>) encoded value as a
 * stream of tokens. This stream includes both literal values (strings, numbers, booleans, and
 * nulls) as well as the begin and end delimiters of objects and arrays. The tokens are traversed in
 * depth-first order, the same order that they appear in the JSON document. Within JSON objects,
 * name/value pairs are represented by a single token.
 *
 * <h2>Parsing JSON</h2>
 *
 * To create a recursive descent parser for your own JSON streams, first create an entry point
 * method that creates a {@code JsonReader}.
 *
 * <p>Next, create handler methods for each structure in your JSON text. You'll need a method for
 * each object type and for each array type.
 *
 * <ul>
 *   <li>Within <strong>array handling</strong> methods, first call {@link #beginArray} to consume
 *       the array's opening bracket. Then create a while loop that accumulates values, terminating
 *       when {@link #hasNext} is false. Finally, read the array's closing bracket by calling {@link
 *       #endArray}.
 *   <li>Within <strong>object handling</strong> methods, first call {@link #beginObject} to consume
 *       the object's opening brace. Then create a while loop that assigns values to local variables
 *       based on their name. This loop should terminate when {@link #hasNext} is false. Finally,
 *       read the object's closing brace by calling {@link #endObject}.
 * </ul>
 *
 * <p>When a nested object or array is encountered, delegate to the corresponding handler method.
 *
 * <p>When an unknown name is encountered, strict parsers should fail with an exception. Lenient
 * parsers should call {@link #skipValue()} to recursively skip the value's nested tokens, which may
 * otherwise conflict.
 *
 * <p>If a value may be null, you should first check using {@link #peek()}. Null literals can be
 * consumed using either {@link #nextNull()} or {@link #skipValue()}.
 *
 * <h2>Example</h2>
 *
 * Suppose we'd like to parse a stream of messages such as the following:
 *
 * <pre>{@code
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I read a JSON stream in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *      }
 *   },
 *   {
 *     "id": 912345678902,
 *     "text": "@json_newb just use JsonReader!",
 *     "geo": [50.454722, -104.606667],
 *     "user": {
 *       "name": "jesse",
 *       "followers_count": 2
 *     }
 *   }
 * ]
 * }</pre>
 *
 * This code implements the parser for the above structure:
 *
 * <pre>{@code
 * public List<Message> readJsonStream(BufferedSource source) throws IOException {
 *   JsonReader reader = JsonReader.of(source);
 *   try {
 *     return readMessagesArray(reader);
 *   } finally {
 *     reader.close();
 *   }
 * }
 *
 * public List<Message> readMessagesArray(JsonReader reader) throws IOException {
 *   List<Message> messages = new ArrayList<Message>();
 *
 *   reader.beginArray();
 *   while (reader.hasNext()) {
 *     messages.add(readMessage(reader));
 *   }
 *   reader.endArray();
 *   return messages;
 * }
 *
 * public Message readMessage(JsonReader reader) throws IOException {
 *   long id = -1;
 *   String text = null;
 *   User user = null;
 *   List<Double> geo = null;
 *
 *   reader.beginObject();
 *   while (reader.hasNext()) {
 *     String name = reader.nextName();
 *     if (name.equals("id")) {
 *       id = reader.nextLong();
 *     } else if (name.equals("text")) {
 *       text = reader.nextString();
 *     } else if (name.equals("geo") && reader.peek() != Token.NULL) {
 *       geo = readDoublesArray(reader);
 *     } else if (name.equals("user")) {
 *       user = readUser(reader);
 *     } else {
 *       reader.skipValue();
 *     }
 *   }
 *   reader.endObject();
 *   return new Message(id, text, user, geo);
 * }
 *
 * public List<Double> readDoublesArray(JsonReader reader) throws IOException {
 *   List<Double> doubles = new ArrayList<Double>();
 *
 *   reader.beginArray();
 *   while (reader.hasNext()) {
 *     doubles.add(reader.nextDouble());
 *   }
 *   reader.endArray();
 *   return doubles;
 * }
 *
 * public User readUser(JsonReader reader) throws IOException {
 *   String username = null;
 *   int followersCount = -1;
 *
 *   reader.beginObject();
 *   while (reader.hasNext()) {
 *     String name = reader.nextName();
 *     if (name.equals("name")) {
 *       username = reader.nextString();
 *     } else if (name.equals("followers_count")) {
 *       followersCount = reader.nextInt();
 *     } else {
 *       reader.skipValue();
 *     }
 *   }
 *   reader.endObject();
 *   return new User(username, followersCount);
 * }
 * }</pre>
 *
 * <h2>Number Handling</h2>
 *
 * This reader permits numeric values to be read as strings and string values to be read as numbers.
 * For example, both elements of the JSON array {@code [1, "1"]} may be read using either {@link
 * #nextInt} or {@link #nextString}. This behavior is intended to prevent lossy numeric conversions:
 * double is JavaScript's only numeric type and very large values like {@code 9007199254740993}
 * cannot be represented exactly on that platform. To minimize precision loss, extremely large
 * values should be written and read as strings in JSON.
 *
 * <p>Each {@code JsonReader} may be used to read a single JSON stream. Instances of this class are
 * not thread safe.
 */
public abstract class JsonReader implements Closeable {
  // The nesting stack. Using a manual array rather than an ArrayList saves 20%. This stack will
  // grow itself up to 256 levels of nesting including the top-level document. Deeper nesting is
  // prone to trigger StackOverflowErrors.
  int stackSize;
  int[] scopes;
  String[] pathNames;
  int[] pathIndices;

  /** True to accept non-spec compliant JSON. */
  boolean lenient;

  /** True to throw a {@link JsonDataException} on any attempt to call {@link #skipValue()}. */
  boolean failOnUnknown;

  private Map<Class<?>, Object> tags;

  /** Returns a new instance that reads UTF-8 encoded JSON from {@code source}. */
  @CheckReturnValue
  public static JsonReader of(BufferedSource source) {
    return new JsonUtf8Reader(source);
  }

  // Package-private to control subclasses.
  JsonReader() {
    scopes = new int[32];
    pathNames = new String[32];
    pathIndices = new int[32];
  }

  // Package-private to control subclasses.
  JsonReader(JsonReader copyFrom) {
    this.stackSize = copyFrom.stackSize;
    this.scopes = copyFrom.scopes.clone();
    this.pathNames = copyFrom.pathNames.clone();
    this.pathIndices = copyFrom.pathIndices.clone();
    this.lenient = copyFrom.lenient;
    this.failOnUnknown = copyFrom.failOnUnknown;
  }

  final void pushScope(int newTop) {
    if (stackSize == scopes.length) {
      if (stackSize == 256) {
        throw new JsonDataException("Nesting too deep at " + getPath());
      }
      scopes = Arrays.copyOf(scopes, scopes.length * 2);
      pathNames = Arrays.copyOf(pathNames, pathNames.length * 2);
      pathIndices = Arrays.copyOf(pathIndices, pathIndices.length * 2);
    }
    scopes[stackSize++] = newTop;
  }

  /**
   * Throws a new IO exception with the given message and a context snippet with this reader's
   * content.
   */
  final JsonEncodingException syntaxError(String message) throws JsonEncodingException {
    throw new JsonEncodingException(message + " at path " + getPath());
  }

  final JsonDataException typeMismatch(@Nullable Object value, Object expected) {
    if (value == null) {
      return new JsonDataException("Expected " + expected + " but was null at path " + getPath());
    } else {
      return new JsonDataException(
          "Expected "
              + expected
              + " but was "
              + value
              + ", a "
              + value.getClass().getName()
              + ", at path "
              + getPath());
    }
  }

  /**
   * Configure this parser to be liberal in what it accepts. By default this parser is strict and
   * only accepts JSON as specified by <a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>.
   * Setting the parser to lenient causes it to ignore the following syntax errors:
   *
   * <ul>
   *   <li>Streams that include multiple top-level values. With strict parsing, each stream must
   *       contain exactly one top-level value.
   *   <li>Numbers may be {@linkplain Double#isNaN() NaNs} or {@link Double#isInfinite()
   *       infinities}.
   *   <li>End of line comments starting with {@code //} or {@code #} and ending with a newline
   *       character.
   *   <li>C-style comments starting with {@code /*} and ending with {@code *}{@code /}. Such
   *       comments may not be nested.
   *   <li>Names that are unquoted or {@code 'single quoted'}.
   *   <li>Strings that are unquoted or {@code 'single quoted'}.
   *   <li>Array elements separated by {@code ;} instead of {@code ,}.
   *   <li>Unnecessary array separators. These are interpreted as if null was the omitted value.
   *   <li>Names and values separated by {@code =} or {@code =>} instead of {@code :}.
   *   <li>Name/value pairs separated by {@code ;} instead of {@code ,}.
   * </ul>
   */
  public final void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  /** Returns true if this parser is liberal in what it accepts. */
  @CheckReturnValue
  public final boolean isLenient() {
    return lenient;
  }

  /**
   * Configure whether this parser throws a {@link JsonDataException} when {@link #skipValue} is
   * called. By default this parser permits values to be skipped.
   *
   * <p>Forbid skipping to prevent unrecognized values from being silently ignored. This option is
   * useful in development and debugging because it means a typo like "locatiom" will be detected
   * early. It's potentially harmful in production because it complicates revising a JSON schema.
   */
  public final void setFailOnUnknown(boolean failOnUnknown) {
    this.failOnUnknown = failOnUnknown;
  }

  /** Returns true if this parser forbids skipping names and values. */
  @CheckReturnValue
  public final boolean failOnUnknown() {
    return failOnUnknown;
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
   * array.
   */
  public abstract void beginArray() throws IOException;

  /**
   * Consumes the next token from the JSON stream and asserts that it is the end of the current
   * array.
   */
  public abstract void endArray() throws IOException;

  /**
   * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
   * object.
   */
  public abstract void beginObject() throws IOException;

  /**
   * Consumes the next token from the JSON stream and asserts that it is the end of the current
   * object.
   */
  public abstract void endObject() throws IOException;

  /** Returns true if the current array or object has another element. */
  @CheckReturnValue
  public abstract boolean hasNext() throws IOException;

  /** Returns the type of the next token without consuming it. */
  @CheckReturnValue
  public abstract Token peek() throws IOException;

  /**
   * Returns the next token, a {@linkplain Token#NAME property name}, and consumes it.
   *
   * @throws JsonDataException if the next token in the stream is not a property name.
   */
  @CheckReturnValue
  public abstract String nextName() throws IOException;

  /**
   * If the next token is a {@linkplain Token#NAME property name} that's in {@code options}, this
   * consumes it and returns its index. Otherwise this returns -1 and no name is consumed.
   */
  @CheckReturnValue
  public abstract int selectName(Options options) throws IOException;

  /**
   * Skips the next token, consuming it. This method is intended for use when the JSON token stream
   * contains unrecognized or unhandled names.
   *
   * <p>This throws a {@link JsonDataException} if this parser has been configured to {@linkplain
   * #failOnUnknown fail on unknown} names.
   */
  public abstract void skipName() throws IOException;

  /**
   * Returns the {@linkplain Token#STRING string} value of the next token, consuming it. If the next
   * token is a number, this method will return its string form.
   *
   * @throws JsonDataException if the next token is not a string or if this reader is closed.
   */
  public abstract String nextString() throws IOException;

  /**
   * If the next token is a {@linkplain Token#STRING string} that's in {@code options}, this
   * consumes it and returns its index. Otherwise this returns -1 and no string is consumed.
   */
  @CheckReturnValue
  public abstract int selectString(Options options) throws IOException;

  /**
   * Returns the {@linkplain Token#BOOLEAN boolean} value of the next token, consuming it.
   *
   * @throws JsonDataException if the next token is not a boolean or if this reader is closed.
   */
  public abstract boolean nextBoolean() throws IOException;

  /**
   * Consumes the next token from the JSON stream and asserts that it is a literal null. Returns
   * null.
   *
   * @throws JsonDataException if the next token is not null or if this reader is closed.
   */
  public abstract @Nullable <T> T nextNull() throws IOException;

  /**
   * Returns the {@linkplain Token#NUMBER double} value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as a double using {@link
   * Double#parseDouble(String)}.
   *
   * @throws JsonDataException if the next token is not a literal value, or if the next literal
   *     value cannot be parsed as a double, or is non-finite.
   */
  public abstract double nextDouble() throws IOException;

  /**
   * Returns the {@linkplain Token#NUMBER long} value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as a long. If the next token's numeric
   * value cannot be exactly represented by a Java {@code long}, this method throws.
   *
   * @throws JsonDataException if the next token is not a literal value, if the next literal value
   *     cannot be parsed as a number, or exactly represented as a long.
   */
  public abstract long nextLong() throws IOException;

  /**
   * Returns the {@linkplain Token#NUMBER int} value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as an int. If the next token's numeric
   * value cannot be exactly represented by a Java {@code int}, this method throws.
   *
   * @throws JsonDataException if the next token is not a literal value, if the next literal value
   *     cannot be parsed as a number, or exactly represented as an int.
   */
  public abstract int nextInt() throws IOException;

  /**
   * Returns the next value as a stream of UTF-8 bytes and consumes it.
   *
   * <p>The following program demonstrates how JSON bytes are returned from an enclosing stream as
   * their original bytes, including their original whitespace:
   *
   * <pre>{@code
   * String json = "{\"a\": [4,  5  ,6.0, {\"x\":7}, 8], \"b\": 9}";
   * JsonReader reader = JsonReader.of(new Buffer().writeUtf8(json));
   * reader.beginObject();
   * assertThat(reader.nextName()).isEqualTo("a");
   * try (BufferedSource bufferedSource = reader.nextSource()) {
   *   assertThat(bufferedSource.readUtf8()).isEqualTo("[4,  5  ,6.0, {\"x\":7}, 8]");
   * }
   * assertThat(reader.nextName()).isEqualTo("b");
   * assertThat(reader.nextInt()).isEqualTo(9);
   * reader.endObject();
   * }</pre>
   *
   * <p>This reads an entire value: composite objects like arrays and objects are returned in their
   * entirety. The stream starts with the first character of the value (typically {@code [}, <code>{
   * </code>, or {@code "}) and ends with the last character of the object (typically {@code ]},
   * <code>}</code>, or {@code "}).
   *
   * <p>The returned source may not be used after any other method on this {@code JsonReader} is
   * called. For example, the following code crashes with an exception:
   *
   * <pre>{@code
   * JsonReader reader = ...
   * reader.beginArray();
   * BufferedSource source = reader.nextSource();
   * reader.endArray();
   * source.readUtf8(); // Crash!
   * }</pre>
   *
   * <p>The returned bytes are not validated. This method assumes the stream is well-formed JSON and
   * only attempts to find the value's boundary in the byte stream. It is the caller's
   * responsibility to check that the returned byte stream is a valid JSON value.
   *
   * <p>Closing the returned source <strong>does not</strong> close this reader.
   */
  public abstract BufferedSource nextSource() throws IOException;

  /**
   * Skips the next value recursively. If it is an object or array, all nested elements are skipped.
   * This method is intended for use when the JSON token stream contains unrecognized or unhandled
   * values.
   *
   * <p>This throws a {@link JsonDataException} if this parser has been configured to {@linkplain
   * #failOnUnknown fail on unknown} values.
   */
  public abstract void skipValue() throws IOException;

  /**
   * Returns the value of the next token, consuming it. The result may be a string, number, boolean,
   * null, map, or list, according to the JSON structure.
   *
   * @throws JsonDataException if the next token is not a literal value, if a JSON object has a
   *     duplicate key.
   * @see JsonWriter#jsonValue(Object)
   */
  public final @Nullable Object readJsonValue() throws IOException {
    switch (peek()) {
      case BEGIN_ARRAY:
        List<Object> list = new ArrayList<>();
        beginArray();
        while (hasNext()) {
          list.add(readJsonValue());
        }
        endArray();
        return list;

      case BEGIN_OBJECT:
        Map<String, Object> map = new LinkedHashTreeMap<>();
        beginObject();
        while (hasNext()) {
          String name = nextName();
          Object value = readJsonValue();
          Object replaced = map.put(name, value);
          if (replaced != null) {
            throw new JsonDataException(
                "Map key '"
                    + name
                    + "' has multiple values at path "
                    + getPath()
                    + ": "
                    + replaced
                    + " and "
                    + value);
          }
        }
        endObject();
        return map;

      case STRING:
        return nextString();

      case NUMBER:
        return nextDouble();

      case BOOLEAN:
        return nextBoolean();

      case NULL:
        return nextNull();

      default:
        throw new IllegalStateException(
            "Expected a value but was " + peek() + " at path " + getPath());
    }
  }

  /**
   * Returns a new {@code JsonReader} that can read data from this {@code JsonReader} without
   * consuming it. The returned reader becomes invalid once this one is next read or closed.
   *
   * <p>For example, we can use {@code peekJson()} to lookahead and read the same data multiple
   * times.
   *
   * <pre>{@code
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("[123, 456, 789]")
   *
   * JsonReader jsonReader = JsonReader.of(buffer);
   * jsonReader.beginArray();
   * jsonReader.nextInt(); // Returns 123, reader contains 456, 789 and ].
   *
   * JsonReader peek = reader.peekJson();
   * peek.nextInt() // Returns 456.
   * peek.nextInt() // Returns 789.
   * peek.endArray()
   *
   * jsonReader.nextInt() // Returns 456, reader contains 789 and ].
   * }</pre>
   */
  @CheckReturnValue
  public abstract JsonReader peekJson();

  /**
   * Returns a <a href="http://goessner.net/articles/JsonPath/">JsonPath</a> to the current location
   * in the JSON value.
   */
  @CheckReturnValue
  public final String getPath() {
    return JsonScope.getPath(stackSize, scopes, pathNames, pathIndices);
  }

  /** Returns the tag value for the given class key. */
  @SuppressWarnings("unchecked")
  @CheckReturnValue
  public final @Nullable <T> T tag(Class<T> clazz) {
    if (tags == null) {
      return null;
    }
    return (T) tags.get(clazz);
  }

  /** Assigns the tag value using the given class key and value. */
  public final <T> void setTag(Class<T> clazz, T value) {
    if (!clazz.isAssignableFrom(value.getClass())) {
      throw new IllegalArgumentException("Tag value must be of type " + clazz.getName());
    }
    if (tags == null) {
      tags = new LinkedHashMap<>();
    }
    tags.put(clazz, value);
  }

  /**
   * Changes the reader to treat the next name as a string value. This is useful for map adapters so
   * that arbitrary type adapters can use {@link #nextString} to read a name value.
   *
   * <p>In this example, calling this method allows two sequential calls to {@link #nextString()}:
   *
   * <pre>{@code
   * JsonReader reader = JsonReader.of(new Buffer().writeUtf8("{\"a\":\"b\"}"));
   * reader.beginObject();
   * reader.promoteNameToValue();
   * assertEquals("a", reader.nextString());
   * assertEquals("b", reader.nextString());
   * reader.endObject();
   * }</pre>
   */
  public abstract void promoteNameToValue() throws IOException;

  /**
   * A set of strings to be chosen with {@link #selectName} or {@link #selectString}. This prepares
   * the encoded values of the strings so they can be read directly from the input source.
   */
  public static final class Options {
    final String[] strings;
    final okio.Options doubleQuoteSuffix;

    private Options(String[] strings, okio.Options doubleQuoteSuffix) {
      this.strings = strings;
      this.doubleQuoteSuffix = doubleQuoteSuffix;
    }

    /** Returns a copy of this {@link Options Option's} strings. */
    public List<String> strings() {
      return unmodifiableList(Arrays.asList(strings));
    }

    @CheckReturnValue
    public static Options of(String... strings) {
      try {
        ByteString[] result = new ByteString[strings.length];
        Buffer buffer = new Buffer();
        for (int i = 0; i < strings.length; i++) {
          JsonUtf8Writer.string(buffer, strings[i]);
          buffer.readByte(); // Skip the leading double quote (but leave the trailing one).
          result[i] = buffer.readByteString();
        }
        return new Options(strings.clone(), okio.Options.of(result));
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  /** A structure, name, or value type in a JSON-encoded string. */
  public enum Token {

    /**
     * The opening of a JSON array. Written using {@link JsonWriter#beginArray} and read using
     * {@link JsonReader#beginArray}.
     */
    BEGIN_ARRAY,

    /**
     * The closing of a JSON array. Written using {@link JsonWriter#endArray} and read using {@link
     * JsonReader#endArray}.
     */
    END_ARRAY,

    /**
     * The opening of a JSON object. Written using {@link JsonWriter#beginObject} and read using
     * {@link JsonReader#beginObject}.
     */
    BEGIN_OBJECT,

    /**
     * The closing of a JSON object. Written using {@link JsonWriter#endObject} and read using
     * {@link JsonReader#endObject}.
     */
    END_OBJECT,

    /**
     * A JSON property name. Within objects, tokens alternate between names and their values.
     * Written using {@link JsonWriter#name} and read using {@link JsonReader#nextName}
     */
    NAME,

    /** A JSON string. */
    STRING,

    /**
     * A JSON number represented in this API by a Java {@code double}, {@code long}, or {@code int}.
     */
    NUMBER,

    /** A JSON {@code true} or {@code false}. */
    BOOLEAN,

    /** A JSON {@code null}. */
    NULL,

    /**
     * The end of the JSON stream. This sentinel value is returned by {@link JsonReader#peek()} to
     * signal that the JSON-encoded value has no more tokens.
     */
    END_DOCUMENT
  }
}
