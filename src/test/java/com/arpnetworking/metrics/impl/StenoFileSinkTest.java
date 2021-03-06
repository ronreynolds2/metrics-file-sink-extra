/**
 * Copyright 2015 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.ComplexCompoundUnit;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;
import com.arpnetworking.metrics.Units;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * Tests for <code>StenoFileSink</code>.
 *
 * @deprecated the Steno format will be removed in a future release.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Deprecated
@SuppressWarnings("deprecation")
public class StenoFileSinkTest {

    @Test
    public void testObjectMapperIOException() throws IOException {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final ObjectMapper objectMapper = Mockito.spy(new ObjectMapper());
        final Sink sink = new StenoFileSink(
                new StenoFileSink.Builder()
                        .setDirectory(createDirectory("./target/StenoFileSinkTest"))
                        .setName("testObjectMapperIOException-Query"),
                objectMapper,
                logger);

        Mockito.doThrow(new JsonMappingException(Mockito.mock(JsonParser.class), "JsonMappingException"))
                .when(objectMapper)
                .writeValueAsString(Mockito.any());
        recordEmpty(sink);
        Mockito.verify(logger).warn(
                Mockito.any(String.class),
                Mockito.any(Throwable.class));
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void testEmptySerialization() throws IOException, InterruptedException {
        final File actualFile = new File("./target/StenoFileSinkTest/testEmptySerialization-Query.log");
        Files.deleteIfExists(actualFile.toPath());
        final Sink sink = new StenoFileSink.Builder()
                .setDirectory(createDirectory("./target/StenoFileSinkTest"))
                .setName("testEmptySerialization-Query")
                .setImmediateFlush(Boolean.TRUE)
                .setAsync(false)
                .build();

        sink.record(new TsdEvent(
                ANNOTATIONS,
                TEST_EMPTY_SERIALIZATION_TIMERS,
                TEST_EMPTY_SERIALIZATION_COUNTERS,
                TEST_EMPTY_SERIALIZATION_GAUGES));

        // TODO(vkoskela): Add protected option to disable async [MAI-181].
        Thread.sleep(100);

        final String actualOriginalJson = fileToString(actualFile);
        assertMatchesJsonSchema(actualOriginalJson);
        final String actualComparableJson = actualOriginalJson
                .replaceAll("\"time\":\"[^\"]*\"", "\"time\":\"<TIME>\"")
                .replaceAll("\"threadId\":\"[^\"]*\"", "\"threadId\":\"<THREADID>\"")
                .replaceAll("\"processId\":\"[^\"]*\"", "\"processId\":\"<PROCESSID>\"")
                .replaceAll("\"host\":\"[^\"]*\"", "\"host\":\"<HOST>\"")
                .replaceAll("\"id\":\"[^\"]*\"", "\"id\":\"<ID>\"");
        final JsonNode actual = OBJECT_MAPPER.readTree(actualComparableJson);
        final JsonNode expected = OBJECT_MAPPER.readTree(EXPECTED_EMPTY_METRICS_JSON);

        Assert.assertEquals(
                "expectedJson=" + OBJECT_MAPPER.writeValueAsString(expected)
                        + " vs actualJson=" + OBJECT_MAPPER.writeValueAsString(actual),
                expected,
                actual);
    }

    @Test
    public void testSerialization() throws IOException, InterruptedException {
        final File actualFile = new File("./target/StenoFileSinkTest/testSerialization-Query.log");
        Files.deleteIfExists(actualFile.toPath());
        final Sink sink = new StenoFileSink.Builder()
                .setDirectory(createDirectory("./target/StenoFileSinkTest"))
                .setName("testSerialization-Query")
                .setImmediateFlush(Boolean.TRUE)
                .setAsync(false)
                .build();

        final Map<String, String> annotations = new LinkedHashMap<>(ANNOTATIONS);
        annotations.put("foo", "bar");
        sink.record(new TsdEvent(
                annotations,
                TEST_SERIALIZATION_TIMERS,
                TEST_SERIALIZATION_COUNTERS,
                TEST_SERIALIZATION_GAUGES));

        // TODO(vkoskela): Add protected option to disable async [MAI-181].
        Thread.sleep(100);

        final String actualOriginalJson = fileToString(actualFile);
        assertMatchesJsonSchema(actualOriginalJson);
        final String actualComparableJson = actualOriginalJson
                .replaceAll("\"time\":\"[^\"]*\"", "\"time\":\"<TIME>\"")
                .replaceAll("\"threadId\":\"[^\"]*\"", "\"threadId\":\"<THREADID>\"")
                .replaceAll("\"processId\":\"[^\"]*\"", "\"processId\":\"<PROCESSID>\"")
                .replaceAll("\"host\":\"[^\"]*\"", "\"host\":\"<HOST>\"")
                .replaceAll("\"id\":\"[^\"]*\"", "\"id\":\"<ID>\"");
        final JsonNode actual = OBJECT_MAPPER.readTree(actualComparableJson);
        final JsonNode expected = OBJECT_MAPPER.readTree(EXPECTED_METRICS_JSON);

        Assert.assertEquals(
                "expectedJson=" + OBJECT_MAPPER.writeValueAsString(expected)
                        + " vs actualJson=" + OBJECT_MAPPER.writeValueAsString(actual),
                expected,
                actual);
    }

    private static Map<String, List<Quantity>> createQuantityMap(final Object... arguments) {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, List<Quantity>> map = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        List<Quantity> samples = Collections.emptyList();
        for (final Object argument : arguments) {
            if (argument instanceof String) {
                samples = new ArrayList<>();
                map.put((String) argument, samples);
            } else if (argument instanceof Quantity) {
                samples.add((Quantity) argument);
            } else {
                assert false : "unsupported argument type: " + argument.getClass();
            }
        }
        return map;
    }

    private void recordEmpty(final Sink sink) {
        sink.record(new TsdEvent(
                Collections.<String, String>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap()));
    }

    private org.slf4j.Logger createSlf4jLoggerMock() {
        return Mockito.mock(org.slf4j.Logger.class);
    }

    private void assertMatchesJsonSchema(final String json) {
        try {
            final JsonNode jsonNode = JsonLoader.fromString(json);
            final ProcessingReport report = VALIDATOR.validate(STENO_SCHEMA, jsonNode);
            Assert.assertTrue(report.toString(), report.isSuccess());
        } catch (final IOException | ProcessingException e) {
            Assert.fail("Failed with exception: " + e);
        }
    }

    private String fileToString(final File file) {
        try {
            return new Scanner(file, "UTF-8").useDelimiter("\\Z").next();
        } catch (final IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static File createDirectory(final String path) throws IOException {
        final File directory = new File(path);
        Files.createDirectories(directory.toPath());
        return directory;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final JsonValidator VALIDATOR = JsonSchemaFactory.byDefault().getValidator();
    private static final JsonNode STENO_SCHEMA;

    private static final Map<String, String> ANNOTATIONS = new LinkedHashMap<>();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_TIMERS = createQuantityMap();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_COUNTERS = createQuantityMap();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_GAUGES = createQuantityMap();

    private static final String EXPECTED_EMPTY_METRICS_JSON = "{"
            + "  \"time\":\"<TIME>\","
            + "  \"name\":\"aint.metrics\","
            + "  \"level\":\"info\","
            + "  \"data\":{"
            + "    \"version\":\"2f\","
            + "    \"annotations\":{"
            + "      \"_start\":\"1997-07-16T19:20:30Z\","
            + "      \"_end\":\"1997-07-16T19:20:31Z\","
            + "      \"_service\":\"MyService\","
            + "      \"_cluster\":\"MyCluster\""
            + "    }"
            + "  },"
            + "  \"context\":{"
            + "    \"threadId\":\"<THREADID>\","
            + "    \"processId\":\"<PROCESSID>\","
            + "    \"host\":\"<HOST>\""
            + "  },"
            + "  \"id\":\"<ID>\","
            + "  \"version\":\"0\""
            + "}";

    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_TIMERS = createQuantityMap(
            "timerA",
            "timerB",
            TsdQuantity.newInstance(1L, null),
            "timerC",
            TsdQuantity.newInstance(2L, Units.MILLISECOND),
            "timerD",
            TsdQuantity.newInstance(3L, Units.SECOND),
            TsdQuantity.newInstance(4L, Units.SECOND),
            "timerE",
            TsdQuantity.newInstance(5L, Units.DAY),
            TsdQuantity.newInstance(6L, Units.SECOND),
            "timerF",
            TsdQuantity.newInstance(7L, Units.DAY),
            TsdQuantity.newInstance(8L, null),
            "timerG",
            TsdQuantity.newInstance(9L, null),
            TsdQuantity.newInstance(10L, null),
            "timerH",
            TsdQuantity.newInstance(11L, Units.DAY),
            TsdQuantity.newInstance(12L, Units.BYTE),
            "timerI",
            TsdQuantity.newInstance(1.12, null),
            "timerJ",
            TsdQuantity.newInstance(2.12, Units.MILLISECOND),
            "timerK",
            TsdQuantity.newInstance(3.12, Units.SECOND),
            TsdQuantity.newInstance(4.12, Units.SECOND),
            "timerL",
            TsdQuantity.newInstance(5.12, Units.DAY),
            TsdQuantity.newInstance(6.12, Units.SECOND),
            "timerM",
            TsdQuantity.newInstance(7.12, Units.DAY),
            TsdQuantity.newInstance(8.12, null),
            "timerN",
            TsdQuantity.newInstance(9.12, null),
            TsdQuantity.newInstance(10.12, null),
            "timerO",
            TsdQuantity.newInstance(11.12, Units.DAY),
            TsdQuantity.newInstance(12.12, Units.BYTE),
            "timerP1",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "timerP2",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "timerP3",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "timerP4",
            TsdQuantity.newInstance(
                    3,
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "timerP5",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()),
            "timerP6",
            TsdQuantity.newInstance(
                    3,
                    new ComplexCompoundUnit(
                            "CustomByteOverByte",
                            Arrays.asList(Units.BYTE),
                            Arrays.asList(Units.BYTE))));

    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_COUNTERS = createQuantityMap(
            "counterA",
            "counterB",
            TsdQuantity.newInstance(11L, null),
            "counterC",
            TsdQuantity.newInstance(12L, Units.MILLISECOND),
            "counterD",
            TsdQuantity.newInstance(13L, Units.SECOND),
            TsdQuantity.newInstance(14L, Units.SECOND),
            "counterE",
            TsdQuantity.newInstance(15L, Units.DAY),
            TsdQuantity.newInstance(16L, Units.SECOND),
            "counterF",
            TsdQuantity.newInstance(17L, Units.DAY),
            TsdQuantity.newInstance(18L, null),
            "counterG",
            TsdQuantity.newInstance(19L, null),
            TsdQuantity.newInstance(110L, null),
            "counterH",
            TsdQuantity.newInstance(111L, Units.DAY),
            TsdQuantity.newInstance(112L, Units.BYTE),
            "counterI",
            TsdQuantity.newInstance(11.12, null),
            "counterJ",
            TsdQuantity.newInstance(12.12, Units.MILLISECOND),
            "counterK",
            TsdQuantity.newInstance(13.12, Units.SECOND),
            TsdQuantity.newInstance(14.12, Units.SECOND),
            "counterL",
            TsdQuantity.newInstance(15.12, Units.DAY),
            TsdQuantity.newInstance(16.12, Units.SECOND),
            "counterM",
            TsdQuantity.newInstance(17.12, Units.DAY),
            TsdQuantity.newInstance(18.12, null),
            "counterN",
            TsdQuantity.newInstance(19.12, null),
            TsdQuantity.newInstance(110.12, null),
            "counterO",
            TsdQuantity.newInstance(111.12, Units.DAY),
            TsdQuantity.newInstance(112.12, Units.BYTE),
            "counterP1",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "counterP2",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "counterP3",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "counterP4",
            TsdQuantity.newInstance(
                    3,
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "counterP5",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()));

    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_GAUGES = createQuantityMap(
            "gaugeA",
            "gaugeB",
            TsdQuantity.newInstance(21L, null),
            "gaugeC",
            TsdQuantity.newInstance(22L, Units.MILLISECOND),
            "gaugeD",
            TsdQuantity.newInstance(23L, Units.SECOND),
            TsdQuantity.newInstance(24L, Units.SECOND),
            "gaugeE",
            TsdQuantity.newInstance(25L, Units.DAY),
            TsdQuantity.newInstance(26L, Units.SECOND),
            "gaugeF",
            TsdQuantity.newInstance(27L, Units.DAY),
            TsdQuantity.newInstance(28L, null),
            "gaugeG",
            TsdQuantity.newInstance(29L, null),
            TsdQuantity.newInstance(210L, null),
            "gaugeH",
            TsdQuantity.newInstance(211L, Units.DAY),
            TsdQuantity.newInstance(212L, Units.BYTE),
            "gaugeI",
            TsdQuantity.newInstance(21.12, null),
            "gaugeJ",
            TsdQuantity.newInstance(22.12, Units.MILLISECOND),
            "gaugeK",
            TsdQuantity.newInstance(23.12, Units.SECOND),
            TsdQuantity.newInstance(24.12, Units.SECOND),
            "gaugeL",
            TsdQuantity.newInstance(25.12, Units.DAY),
            TsdQuantity.newInstance(26.12, Units.SECOND),
            "gaugeM",
            TsdQuantity.newInstance(27.12, Units.DAY),
            TsdQuantity.newInstance(28.12, null),
            "gaugeN",
            TsdQuantity.newInstance(29.12, null),
            TsdQuantity.newInstance(210.12, null),
            "gaugeO",
            TsdQuantity.newInstance(211.12, Units.DAY),
            TsdQuantity.newInstance(212.12, Units.BYTE),
            "gaugeP1",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.KILOBYTE)
                        .addDenominatorUnit(Units.MILLISECOND)
                        .build()),
            "gaugeP2",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "gaugeP3",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "gaugeP4",
            TsdQuantity.newInstance(
                    3,
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "gaugeP5",
            TsdQuantity.newInstance(
                    3,
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()),
            "gaugeP6",
            TsdQuantity.newInstance(
                    3,
                    new ComplexCompoundUnit(
                            "CustomByteOverByte",
                            Arrays.asList(Units.BYTE),
                            Arrays.asList(Units.BYTE))));

    // CHECKSTYLE.OFF: LineLengthCheck - One value per line.
    private static final String EXPECTED_METRICS_JSON = "{"
            + "  \"time\":\"<TIME>\","
            + "  \"name\":\"aint.metrics\","
            + "  \"level\":\"info\","
            + "  \"data\":{"
            + "    \"version\":\"2f\","
            + "    \"annotations\":{"
            + "      \"_start\":\"1997-07-16T19:20:30Z\","
            + "      \"_end\":\"1997-07-16T19:20:31Z\","
            + "      \"_service\":\"MyService\","
            + "      \"_cluster\":\"MyCluster\","
            + "      \"foo\":\"bar\""
            + "    },"
            + "    \"counters\":{"
            + "      \"counterA\":{\"values\":[]},"
            + "      \"counterB\":{\"values\":[{\"value\":11}]},"
            + "      \"counterC\":{\"values\":[{\"value\":12,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"counterD\":{\"values\":[{\"value\":13,\"unitNumerators\":[\"second\"]},{\"value\":14,\"unitNumerators\":[\"second\"]}]},"
            + "      \"counterE\":{\"values\":[{\"value\":15,\"unitNumerators\":[\"day\"]},{\"value\":16,\"unitNumerators\":[\"second\"]}]},"
            + "      \"counterF\":{\"values\":[{\"value\":17,\"unitNumerators\":[\"day\"]},{\"value\":18}]},"
            + "      \"counterG\":{\"values\":[{\"value\":19},{\"value\":110}]},"
            + "      \"counterH\":{\"values\":[{\"value\":111,\"unitNumerators\":[\"day\"]},{\"value\":112,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"counterI\":{\"values\":[{\"value\":11.12}]},"
            + "      \"counterJ\":{\"values\":[{\"value\":12.12,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"counterK\":{\"values\":[{\"value\":13.12,\"unitNumerators\":[\"second\"]},{\"value\":14.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"counterL\":{\"values\":[{\"value\":15.12,\"unitNumerators\":[\"day\"]},{\"value\":16.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"counterM\":{\"values\":[{\"value\":17.12,\"unitNumerators\":[\"day\"]},{\"value\":18.12}]},"
            + "      \"counterN\":{\"values\":[{\"value\":19.12},{\"value\":110.12}]},"
            + "      \"counterO\":{\"values\":[{\"value\":111.12,\"unitNumerators\":[\"day\"]},{\"value\":112.12,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"counterP1\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"],\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"counterP2\":{\"values\":[{\"value\":3,\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"counterP3\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"]}]},"
            + "      \"counterP4\":{\"values\":[{\"value\":3}]},"
            + "      \"counterP5\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"byte\",\"second\"]}]}"
            + "    },"
            + "    \"gauges\":{"
            + "      \"gaugeA\":{\"values\":[]},"
            + "      \"gaugeB\":{\"values\":[{\"value\":21}]},"
            + "      \"gaugeC\":{\"values\":[{\"value\":22,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"gaugeD\":{\"values\":[{\"value\":23,\"unitNumerators\":[\"second\"]},{\"value\":24,\"unitNumerators\":[\"second\"]}]},"
            + "      \"gaugeE\":{\"values\":[{\"value\":25,\"unitNumerators\":[\"day\"]},{\"value\":26,\"unitNumerators\":[\"second\"]}]},"
            + "      \"gaugeF\":{\"values\":[{\"value\":27,\"unitNumerators\":[\"day\"]},{\"value\":28}]},"
            + "      \"gaugeG\":{\"values\":[{\"value\":29},{\"value\":210}]},"
            + "      \"gaugeH\":{\"values\":[{\"value\":211,\"unitNumerators\":[\"day\"]},{\"value\":212,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"gaugeI\":{\"values\":[{\"value\":21.12}]},"
            + "      \"gaugeJ\":{\"values\":[{\"value\":22.12,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"gaugeK\":{\"values\":[{\"value\":23.12,\"unitNumerators\":[\"second\"]},{\"value\":24.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"gaugeL\":{\"values\":[{\"value\":25.12,\"unitNumerators\":[\"day\"]},{\"value\":26.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"gaugeM\":{\"values\":[{\"value\":27.12,\"unitNumerators\":[\"day\"]},{\"value\":28.12}]},"
            + "      \"gaugeN\":{\"values\":[{\"value\":29.12},{\"value\":210.12}]},"
            + "      \"gaugeO\":{\"values\":[{\"value\":211.12,\"unitNumerators\":[\"day\"]},{\"value\":212.12,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"gaugeP1\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"],\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"gaugeP2\":{\"values\":[{\"value\":3,\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"gaugeP3\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"]}]},"
            + "      \"gaugeP4\":{\"values\":[{\"value\":3}]},"
            + "      \"gaugeP5\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"byte\",\"second\"]}]},"
            + "      \"gaugeP6\":{\"values\":[{\"value\":3}]}"
            + "    },"
            + "    \"timers\":{"
            + "     \"timerA\":{\"values\":[]},"
            + "      \"timerB\":{\"values\":[{\"value\":1}]},"
            + "      \"timerC\":{\"values\":[{\"value\":2,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"timerD\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"second\"]},{\"value\":4,\"unitNumerators\":[\"second\"]}]},"
            + "      \"timerE\":{\"values\":[{\"value\":5,\"unitNumerators\":[\"day\"]},{\"value\":6,\"unitNumerators\":[\"second\"]}]},"
            + "      \"timerF\":{\"values\":[{\"value\":7,\"unitNumerators\":[\"day\"]},{\"value\":8}]},"
            + "      \"timerG\":{\"values\":[{\"value\":9},{\"value\":10}]},"
            + "      \"timerH\":{\"values\":[{\"value\":11,\"unitNumerators\":[\"day\"]},{\"value\":12,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"timerI\":{\"values\":[{\"value\":1.12}]},"
            + "      \"timerJ\":{\"values\":[{\"value\":2.12,\"unitNumerators\":[\"millisecond\"]}]},"
            + "      \"timerK\":{\"values\":[{\"value\":3.12,\"unitNumerators\":[\"second\"]},{\"value\":4.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"timerL\":{\"values\":[{\"value\":5.12,\"unitNumerators\":[\"day\"]},{\"value\":6.12,\"unitNumerators\":[\"second\"]}]},"
            + "      \"timerM\":{\"values\":[{\"value\":7.12,\"unitNumerators\":[\"day\"]},{\"value\":8.12}]},"
            + "      \"timerN\":{\"values\":[{\"value\":9.12},{\"value\":10.12}]},"
            + "      \"timerO\":{\"values\":[{\"value\":11.12,\"unitNumerators\":[\"day\"]},{\"value\":12.12,\"unitNumerators\":[\"byte\"]}]},"
            + "      \"timerP1\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"],\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"timerP2\":{\"values\":[{\"value\":3,\"unitDenominators\":[\"millisecond\"]}]},"
            + "      \"timerP3\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"kilobyte\"]}]},"
            + "      \"timerP4\":{\"values\":[{\"value\":3}]},"
            + "      \"timerP5\":{\"values\":[{\"value\":3,\"unitNumerators\":[\"byte\",\"second\"]}]},"
            + "      \"timerP6\":{\"values\":[{\"value\":3}]}"
            + "    }"
            + "  },"
            + "  \"context\":{"
            + "    \"threadId\":\"<THREADID>\","
            + "    \"processId\":\"<PROCESSID>\","
            + "    \"host\":\"<HOST>\""
            + "  },"
            + "  \"id\":\"<ID>\","
            + "  \"version\":\"0\""
            + "}";
    // CHECKSTYLE.ON: LineLengthCheck

    private static final String SCHEMA_FILE_NAME = "query-log-steno-schema-2f.json";

    static {
        JsonNode jsonNode;
        try {
            // Attempt to load the cached copy
            jsonNode = JsonLoader.fromPath("./target/" + SCHEMA_FILE_NAME);
        } catch (final IOException e1) {
            try {
                // Download from the source repository
                jsonNode = JsonLoader.fromURL(
                        new URL("https://raw.githubusercontent.com/ArpNetworking/metrics-client-doc/master/schema/" + SCHEMA_FILE_NAME));

                // Cache the schema file
                Files.write(
                        Paths.get("./target/" + SCHEMA_FILE_NAME),
                        JacksonUtils.prettyPrint(jsonNode).getBytes(Charset.forName("UTF-8")));
            } catch (final IOException e2) {
                throw new RuntimeException(e2);
            }
        }
        STENO_SCHEMA = jsonNode;

        ANNOTATIONS.put("_start", "1997-07-16T19:20:30Z");
        ANNOTATIONS.put("_end", "1997-07-16T19:20:31Z");
        ANNOTATIONS.put("_id", UUID.randomUUID().toString());
        ANNOTATIONS.put("_host", "<HOST>");
        ANNOTATIONS.put("_service", "MyService");
        ANNOTATIONS.put("_cluster", "MyCluster");
    }
}
