// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.test;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.view.test.TestStatus.TestCase;
import com.google.devtools.build.lib.view.test.TestStatus.TestCase.Type;
import com.google.protobuf.UninitializedMessageException;

import java.io.InputStream;
import java.util.Collection;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses a test.xml generated by jUnit or any testing framework
 * into a protocol buffer. The schema of the test.xml is a bit hazy, so there is
 * some guesswork involved.
 */
class TestXmlOutputParser {
  // jUnit can use either "testsuites" or "testsuite".
  private static final Collection<String> TOPLEVEL_ELEMENT_NAMES =
      ImmutableSet.of("testsuites", "testsuite");

  public TestCase parseXmlIntoTestResult(InputStream xmlStream)
      throws TestXmlOutputParserException {
    return parseXmlToTree(xmlStream);
  }

  /**
   * Parses the a test result XML file into the corresponding protocol buffer.
   * @param xmlStream the XML data stream
   * @return the protocol buffer with the parsed data, or null if there was
   * an error while parsing the file.
   *
   * @throws TestXmlOutputParserException when the XML file cannot be parsed
   */
  private TestCase parseXmlToTree(InputStream xmlStream)
      throws TestXmlOutputParserException {
    XMLStreamReader parser = null;

    try {
      parser = XMLInputFactory.newInstance().createXMLStreamReader(xmlStream);

      while (true) {
        int event = parser.next();
        if (event == XMLStreamConstants.END_DOCUMENT) {
          return null;
        }

        // First find the topmost node.
        if (event == XMLStreamConstants.START_ELEMENT) {
          String elementName = parser.getLocalName();
          if (TOPLEVEL_ELEMENT_NAMES.contains(elementName)) {
            TestCase result = parseTestSuite(parser, elementName);
            return result;
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new TestXmlOutputParserException(e);
    }  catch (NumberFormatException e) {
      // The parser is definitely != null here.
      throw new TestXmlOutputParserException(
          "Number could not be parsed at "
          + parser.getLocation().getLineNumber() + ":"
          + parser.getLocation().getColumnNumber(),
          e);
    } catch (UninitializedMessageException e) {
      // This happens when the XML does not contain a field that is required
      // in the protocol buffer
      throw new TestXmlOutputParserException(e);
    } catch (RuntimeException e) {

      // Seems like that an XNIException can leak through, even though it is not
      // specified anywhere.
      //
      // It's a bad idea to refer to XNIException directly because the Xerces
      // documentation says that it may not be available here soon (and it
      // results in a compile-time warning anyway), so we do it the roundabout
      // way: check if the class name has something to do with Xerces, and if
      // so, wrap it in our own exception type, otherwise, let the stack
      // unwinding continue.
      String name = e.getClass().getCanonicalName();
      if (name != null && name.contains("org.apache.xerces")) {
        throw new TestXmlOutputParserException(e);
      } else {
        throw e;
      }
    } finally {
      if (parser != null) {
        try {
          parser.close();
        } catch (XMLStreamException e) {

          // Ignore errors during closure so that we do not interfere with an
          // already propagating exception.
        }
      }
    }
  }

  /**
   * Creates an exception suitable to be thrown when and a bad end tag appears.
   * The exception could also be thrown from here but that would result in an
   * extra stack frame, whereas this way, the topmost frame shows the location
   * where the error occurred.
   */
  private TestXmlOutputParserException createBadElementException(
      String expected, XMLStreamReader parser) {
    return new TestXmlOutputParserException("Expected end of XML element '"
        + expected + "' , but got '" + parser.getLocalName() + "' at "
        + parser.getLocation().getLineNumber() + ":"
        + parser.getLocation().getColumnNumber());
  }

  /**
   * Parses a 'testsuite' element.
   *
   * @throws TestXmlOutputParserException if the XML document is malformed
   * @throws XMLStreamException if there was an error processing the XML
   * @throws NumberFormatException if one of the numeric fields does not contain
   *         a valid number
   */
  private TestCase parseTestSuite(XMLStreamReader parser, String elementName)
      throws XMLStreamException, TestXmlOutputParserException {
    TestCase.Builder builder = TestCase.newBuilder();
    builder.setType(Type.TEST_SUITE);
    for (int i = 0; i < parser.getAttributeCount(); i++) {
      String name = parser.getAttributeLocalName(i).intern();
      String value = parser.getAttributeValue(i);

      if (name.equals("name")) {
        builder.setName(value);
      } else if (name.equals("time")) {
        builder.setRunDurationMillis(parseTime(value));
      }
    }

    parseContainedElements(parser, elementName, builder);
    return builder.build();
  }

  /**
   * Parses a time in test.xml format.
   *
   * @throws NumberFormatException if the time is malformed (i.e. is neither an
   * integer nor a decimal fraction with '.' as the fraction separator)
   */
  private long parseTime(String string) {

    // This is ugly. For Historical Reasons, we have to check whether the number
    // contains a decimal point or not. If it does, the number is expressed in
    // milliseconds, otherwise, in seconds.
    if (string.contains(".")) {
      return Math.round(Float.parseFloat(string) * 1000);
    } else {
      return Long.parseLong(string);
    }
  }

  /**
   * Parses a 'decorator' element.
   *
   * @throws TestXmlOutputParserException if the XML document is malformed
   * @throws XMLStreamException if there was an error processing the XML
   * @throws NumberFormatException if one of the numeric fields does not contain
   *         a valid number
   */
  private TestCase parseTestDecorator(XMLStreamReader parser)
      throws XMLStreamException, TestXmlOutputParserException {
    TestCase.Builder builder = TestCase.newBuilder();
    builder.setType(Type.TEST_DECORATOR);
    for (int i = 0; i < parser.getAttributeCount(); i++) {
      String name = parser.getAttributeLocalName(i);
      String value = parser.getAttributeValue(i);

      builder.setName(name);
      if (name.equals("classname")) {
        builder.setClassName(value);
      } else if (name.equals("time")) {
        builder.setRunDurationMillis(parseTime(value));
      }
    }

    parseContainedElements(parser, "testdecorator", builder);
    return builder.build();
  }

  /**
   * Parses child elements of the specified tag. Strictly speaking, not every
   * element can be a child of every other, but the HierarchicalTestResult can
   * handle that, and (in this case) it does not hurt to be a bit more flexible
   * than necessary.
   *
   * @throws TestXmlOutputParserException if the XML document is malformed
   * @throws XMLStreamException if there was an error processing the XML
   * @throws NumberFormatException if one of the numeric fields does not contain
   *         a valid number
   */
  private void parseContainedElements(
      XMLStreamReader parser, String elementName, TestCase.Builder builder)
      throws XMLStreamException, TestXmlOutputParserException {
    int failures = 0;
    int errors = 0;

    while (true) {
      int event = parser.next();
      switch (event) {
        case XMLStreamConstants.START_ELEMENT:
          String childElementName = parser.getLocalName().intern();

          // We are not parsing four elements here: system-out, system-err,
          // failure and error. They potentially contain useful information, but
          // they can be too big to fit in the memory. We add failure and error
          // elements to the output without a message, so that there is a
          // difference between passed and failed test cases.
          if (childElementName.equals("testsuite")) {
            builder.addChild(parseTestSuite(parser, childElementName));
          } else if (childElementName.equals("testcase")) {
            builder.addChild(parseTestCase(parser));
          } else if (childElementName.equals("failure")) {
            failures += 1;
            skipCompleteElement(parser);
          } else if (childElementName.equals("error")) {
            errors += 1;
            skipCompleteElement(parser);
          } else if (childElementName.equals("testdecorator")) {
            builder.addChild(parseTestDecorator(parser));
          } else {

            // Unknown element encountered. Since the schema of the input file
            // is a bit hazy, just skip it and go merrily on our way. Ignorance
            // is bliss.
            skipCompleteElement(parser);
          }
          break;

        case XMLStreamConstants.END_ELEMENT:
          // Propagate errors/failures from children up to the current case
          for (int i = 0; i < builder.getChildCount(); i += 1) {
            if (builder.getChild(i).getStatus() == TestCase.Status.ERROR) {
              errors += 1;
            }
            if (builder.getChild(i).getStatus() == TestCase.Status.FAILED) {
              failures += 1;
            }
          }

          if (errors > 0) {
            builder.setStatus(TestCase.Status.ERROR);
          } else if (failures > 0) {
            builder.setStatus(TestCase.Status.FAILED);
          } else {
            builder.setStatus(TestCase.Status.PASSED);
          }
          // This is the end tag of the element we are supposed to parse.
          // Hooray, tell our superiors that our mission is complete.
          if (!parser.getLocalName().equals(elementName)) {
            throw createBadElementException(elementName, parser);
          }
          return;
      }
    }
  }


  /**
   * Parses a 'testcase' element.
   *
   * @throws TestXmlOutputParserException if the XML document is malformed
   * @throws XMLStreamException if there was an error processing the XML
   * @throws NumberFormatException if the time field does not contain a valid
   *         number
   */
  private TestCase parseTestCase(XMLStreamReader parser)
      throws XMLStreamException, TestXmlOutputParserException {
    TestCase.Builder builder = TestCase.newBuilder();
    builder.setType(Type.TEST_CASE);
    for (int i = 0; i < parser.getAttributeCount(); i++) {
      String name = parser.getAttributeLocalName(i).intern();
      String value = parser.getAttributeValue(i);

      if (name.equals("name")) {
        builder.setName(value);
      } else if (name.equals("classname")) {
        builder.setClassName(value);
      } else if (name.equals("time")) {
        builder.setRunDurationMillis(parseTime(value));
      } else if (name.equals("result")) {
        builder.setResult(value);
      } else if (name.equals("status")) {
        if (value.equals("notrun")) {
          builder.setRun(false);
        } else if (value.equals("run")) {
          builder.setRun(true);
        }
      }
    }

    parseContainedElements(parser, "testcase", builder);
    return builder.build();
  }

  /**
   * Skips over a complete XML element on the input.
   * Precondition: the cursor is at a START_ELEMENT.
   * Postcondition: the cursor is at an END_ELEMENT.
   *
   * @throws XMLStreamException if the XML is malformed
   */
  private void skipCompleteElement(XMLStreamReader parser) throws XMLStreamException {
    int depth = 1;
    while (true) {
      int event = parser.next();

      switch (event) {
        case XMLStreamConstants.START_ELEMENT:
          depth++;
          break;

        case XMLStreamConstants.END_ELEMENT:
          if (--depth == 0) {
            return;
          }
          break;
      }
    }
  }
}
