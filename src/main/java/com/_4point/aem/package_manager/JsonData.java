package com._4point.aem.package_manager;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;


/**
 * This class is used to store Json Data.
 * 
 * Internally it stores it as a String.
 *
 */
public class JsonData {
	// ObjectMapper is expensive to create but is threadsafe, so only create it once.
	// See: https://stackoverflow.com/questions/57670466/objectmapper-best-practice-for-thread-safety-and-performance
	// Also see: https://stackoverflow.com/questions/3907929/should-i-declare-jacksons-objectmapper-as-a-static-field
	private static final JsonMapper mapper = new JsonMapper();

	private final String jsonData;
	private final JsonNode rootNode;
	
	
	private JsonData(String jsonData, JsonNode rootNode) {
		this.jsonData = jsonData;
		this.rootNode = rootNode;
	}
	
	public JsonData(String jsonData, JsonNode rootNode, Path jsonSchemaFile) {
		this.jsonData = jsonData;
		this.rootNode = rootNode;
	}

	public String asString() {
		return jsonData;
	}
	
	public static JsonData from(String string) {
		try {
			return new JsonData(string, mapper.readTree(string));
		} catch (JacksonException e) {
			throw new JsonDataException("Error while parsing JsonData string.", e);
		}
	}
	
	public static JsonData from(Path schemaFile, String string) {
		try {
			return new JsonData(string, mapper.readTree(string), schemaFile);
		} catch (JacksonException e) {
			throw new JsonDataException("Error while parsing JsonData string.", e);
		}
	}
	
	public String determineRootName() {
		Collection<String> topLevelFieldNames =  rootNode.propertyNames();
		if (topLevelFieldNames.size() != 1) {
			// Should only be one root object.
			throw new IllegalStateException("Expected just one json root but found nodes %s".formatted(topLevelFieldNames.toString()));
		}
		return topLevelFieldNames.stream().findFirst().get();
	}

	/**
	 * Returns the value of the JsonNode pointed at using a JsonPointer string.
	 * 
	 * @param jsonDataPtr
	 * @return
	 */
	public Optional<String> at(JsonDataPointer jsonDataPtr) {
		JsonNode node = rootNode.at(jsonDataPtr.jsonPointer);
		return node.isValueNode() ? Optional.of(node.asString()) : Optional.empty();
	}
	
	/**
	 * Returns the value of the JsonNode pointed at using a JsonPointer string.
	 * 
	 * @param jsonPtr
	 * @return
	 */
	public Optional<String> at(String jsonPtr) {
		return at(JsonDataPointer.of(jsonPtr));
	}
	
	/**
	 * Returns the JsonData of the JsonNode pointed at using a JsonPointer string.
	 * 
	 * @param jsonPtr
	 * @return
	 */
	public Optional<JsonData> subsetAt(String jsonPtr) {
		return subsetAt(JsonDataPointer.of(jsonPtr));
	}
	
	/**
	 * Returns the JsonData of the JsonNode pointed at using a JsonPointer string.
	 * 
	 * @param jsonDataPtr
	 * @return
	 */
	public Optional<JsonData> subsetAt(JsonDataPointer jsonDataPtr) {
		JsonNode node = rootNode.at(jsonDataPtr.jsonPointer);
		return node.isContainer() ? Optional.of(JsonData.from(node.toPrettyString())) : Optional.empty();
	}
	

	/**
	 * Returns true if there is a JsonNode pointed at by the JsonPointer string.
	 * 
	 * This routine works for normal Json data and Adaptive Form Json data.
	 * 
	 * If the JsonData is Adaptive Form Json data (i.e. it has the Adaptive Form wrapper), then it will locate the node
	 * using the "afBound" data node as the root node.  If that is not desired use the at(jsonPtr, false) call instead. 
	 * 
	 * @param jsonPtr
	 * @return
	 */
	public boolean hasNode(String jsonPtr) {
		return hasNode(JsonDataPointer.of(jsonPtr));
	}

	/**
	 * Returns true if there is a JsonNode pointed at by the JsonPointer string.
	 * 
	 * This routine works for normal Json data and Adaptive Form Json data.
	 * 
	 * If the JsonData is Adaptive Form Json data (i.e. it has the Adaptive Form wrapper), then it will locate the node
	 * using the "afBound" data node as the root node.  If that is not desired use the at(jsonPtr, false) call instead. 
	 * 
	 * @param jsonDataPtr
	 * @return
	 */
	public boolean hasNode(JsonDataPointer jsonDataPtr) {
		return !rootNode.at(jsonDataPtr.jsonPointer).isMissingNode();
	}

	/**
	 * Inserts a property containing an object somewhere into the JSON
	 * 
	 * @param jsonPointer
	 * 		pointer to location where the property will be inserted
	 * @param property
	 * 		property to be inserted
	 * @param value
	 * 		json object of the property being inserted
	 * @return
	 * 		copy of the original JsonData with the property inserted.
	 */
	public JsonData insertJsonProperty(String jsonPointer, String property, JsonData value) {
		return insertJsonProperty(JsonDataPointer.of(jsonPointer), property, value);
	}

	/**
	 * Inserts a property with a String value somewhere into the JSON
	 * 
	 * @param jsonPointer
	 * 		pointer to location where the property will be inserted
	 * @param property
	 * 		property to be inserted
	 * @param value
	 * 		value of the property being inserted
	 * @return
	 * 		copy of the original JsonData with the property inserted.
	 */
	public JsonData insertJsonProperty(String jsonPointer, String property, String value) {
		return insertJsonProperty(JsonDataPointer.of(jsonPointer), property, value);
	}	

	/**
	 * Inserts a property containing an object somewhere into the JSON
	 * 
	 * @param jsonDataPointer
	 * 		pointer to location where the property will be inserted
	 * @param property
	 * 		property to be inserted
	 * @param value
	 * 		json object of the property being inserted
	 * @return
	 * 		copy of the original JsonData with the property inserted.
	 */
	public JsonData insertJsonProperty(JsonDataPointer jsonDataPointer, String property, JsonData value) {
		return insert(jsonDataPointer.jsonPointer, n->n.set(property, value.rootNode));
	}

	/**
	 * Inserts a property with a String value somewhere into the JSON
	 * 
	 * @param jsonDataPointer
	 * 		pointer to location where the property will be inserted
	 * @param property
	 * 		property to be inserted
	 * @param value
	 * 		value of the property being inserted
	 * @return
	 * 		copy of the original JsonData with the property inserted.
	 */
	public JsonData insertJsonProperty(JsonDataPointer jsonDataPointer, String property, String value) {
		return insert(jsonDataPointer.jsonPointer, n->n.put(property, value));
	}	

	// Internal routine that locates and inserts something to into the JsonData (returning a copy)
	// The op is a Consumer that takes an ObjectNode and does something to it to insert the new thing.
	private JsonData insert(JsonPointer insertionPoint, Consumer<ObjectNode> op) {
		JsonNode deepCopy = rootNode.deepCopy();
		JsonNode insertionNode = deepCopy.at(insertionPoint);
		if (!insertionNode.isObject()) {
			throw new JsonDataException("Insertion only allowed in existing objects.  Pointer='" + insertionPoint.toString() + "'.");
		}
		op.accept((ObjectNode)insertionNode);
		try {
			return JsonData.from(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deepCopy));
		} catch (JacksonException e) {
			throw new JsonDataException("Error whiole inserting into Json.", e);
		}
	}
	public static class JsonDataPointer {
		private final JsonPointer jsonPointer;

		private JsonDataPointer(JsonPointer jsonPointer) {
			this.jsonPointer = jsonPointer;
		}

		public static JsonDataPointer of(String pointerString) {
			return new JsonDataPointer(JsonPointer.valueOf(pointerString));
		}

		@Override
		public String toString() {
			return jsonPointer.toString();
		}
	}
	
	@SuppressWarnings("serial")
	public static class JsonDataException extends RuntimeException {

		public JsonDataException() {
		}

		public JsonDataException(String message, Throwable cause) {
			super(message, cause);
		}

		public JsonDataException(String message) {
			super(message);
		}

		public JsonDataException(Throwable cause) {
			super(cause);
		}
	}

}
