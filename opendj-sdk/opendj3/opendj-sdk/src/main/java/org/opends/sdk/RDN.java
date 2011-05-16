/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static org.opends.sdk.CoreMessages.ERR_RDN_TYPE_NOT_FOUND;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.opends.sdk.schema.AttributeType;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.schema.UnknownSchemaElementException;

import com.sun.opends.sdk.util.Iterators;
import com.sun.opends.sdk.util.SubstringReader;



/**
 * A relative distinguished name (RDN) as defined in RFC 4512 section 2.3 is the
 * name of an entry relative to its immediate superior. An RDN is composed of an
 * unordered set of one or more attribute value assertions (AVA) consisting of
 * an attribute description with zero options and an attribute value. These AVAs
 * are chosen to match attribute values (each a distinguished value) of the
 * entry.
 * <p>
 * An entry's relative distinguished name must be unique among all immediate
 * subordinates of the entry's immediate superior (i.e. all siblings).
 * <p>
 * The following are examples of string representations of RDNs:
 *
 * <pre>
 * uid=12345
 * ou=Engineering
 * cn=Kurt Zeilenga+L=Redwood Shores
 * </pre>
 *
 * The last is an example of a multi-valued RDN; that is, an RDN composed of
 * multiple AVAs.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC 4512 -
 *      Lightweight Directory Access Protocol (LDAP): Directory Information
 *      Models </a>
 */
public final class RDN implements Iterable<AVA>, Comparable<RDN>
{
  /**
   * Parses the provided LDAP string representation of an RDN using the default
   * schema.
   *
   * @param rdn
   *          The LDAP string representation of a RDN.
   * @return The parsed RDN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code rdn} is not a valid LDAP string representation of a
   *           RDN.
   * @throws NullPointerException
   *           If {@code rdn} was {@code null}.
   */
  public static RDN valueOf(final String rdn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return valueOf(rdn, Schema.getDefaultSchema());
  }



  /**
   * Parses the provided LDAP string representation of a RDN using the provided
   * schema.
   *
   * @param rdn
   *          The LDAP string representation of a RDN.
   * @param schema
   *          The schema to use when parsing the RDN.
   * @return The parsed RDN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code rdn} is not a valid LDAP string representation of a
   *           RDN.
   * @throws NullPointerException
   *           If {@code rdn} or {@code schema} was {@code null}.
   */
  public static RDN valueOf(final String rdn, final Schema schema)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    final SubstringReader reader = new SubstringReader(rdn);
    try
    {
      return decode(rdn, reader, schema);
    }
    catch (final UnknownSchemaElementException e)
    {
      final LocalizableMessage message = ERR_RDN_TYPE_NOT_FOUND.get(rdn, e
          .getMessageObject());
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  // FIXME: ensure that the decoded RDN does not contain multiple AVAs
  // with the same type.
  static RDN decode(final String rdnString, final SubstringReader reader,
      final Schema schema) throws LocalizedIllegalArgumentException,
      UnknownSchemaElementException
  {
    final AVA firstAVA = AVA.decode(reader, schema);

    // Skip over any spaces that might be after the attribute value.
    reader.skipWhitespaces();

    reader.mark();
    if (reader.remaining() > 0 && reader.read() == '+')
    {
      final List<AVA> avas = new ArrayList<AVA>();
      avas.add(firstAVA);

      do
      {
        avas.add(AVA.decode(reader, schema));

        // Skip over any spaces that might be after the attribute value.
        reader.skipWhitespaces();

        reader.mark();
      }
      while (reader.remaining() > 0 && reader.read() == '+');

      reader.reset();
      return new RDN(avas.toArray(new AVA[avas.size()]), null);
    }
    else
    {
      reader.reset();
      return new RDN(new AVA[] { firstAVA }, null);
    }
  }



  // In original order.
  private final AVA[] avas;

  // We need to store the original string value if provided in order to
  // preserve the original whitespace.
  private String stringValue;



  /**
   * Creates a new RDN using the provided attribute type and value.
   *
   * @param attributeType
   *          The attribute type.
   * @param attributeValue
   *          The attribute value.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code attributeValue} was {@code
   *           null}.
   */
  public RDN(final AttributeType attributeType, final ByteString attributeValue)
      throws NullPointerException
  {
    this.avas = new AVA[] { new AVA(attributeType, attributeValue) };
  }



  /**
   * Creates a new RDN using the provided attribute type and value decoded using
   * the default schema.
   * <p>
   * If {@code attributeValue} is not an instance of {@code ByteString} then it
   * will be converted using the {@link ByteString#valueOf(Object)} method.
   *
   * @param attributeType
   *          The attribute type.
   * @param attributeValue
   *          The attribute value.
   * @throws UnknownSchemaElementException
   *           If {@code attributeType} was not found in the default schema.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code attributeValue} was {@code
   *           null}.
   */
  public RDN(final String attributeType, final Object attributeValue)
      throws UnknownSchemaElementException, NullPointerException
  {
    this.avas = new AVA[] { new AVA(attributeType, attributeValue) };
  }



  private RDN(final AVA[] avas, final String stringValue)
  {
    this.avas = avas;
    this.stringValue = stringValue;
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(final RDN rdn)
  {
    final int sz1 = avas.length;
    final int sz2 = rdn.avas.length;

    if (sz1 != sz2)
    {
      return sz1 - sz2 > 0 ? 1 : -1;
    }

    if (sz1 == 1)
    {
      return avas[0].compareTo(rdn.avas[0]);
    }

    // Need to sort the AVAs before comparing.
    final AVA[] a1 = new AVA[sz1];
    System.arraycopy(avas, 0, a1, 0, sz1);
    Arrays.sort(a1);

    final AVA[] a2 = new AVA[sz1];
    System.arraycopy(rdn.avas, 0, a2, 0, sz1);
    Arrays.sort(a2);

    for (int i = 0; i < sz1; i++)
    {
      final int result = a1[i].compareTo(a2[i]);
      if (result != 0)
      {
        return result;
      }
    }

    return 0;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof RDN)
    {
      return compareTo((RDN) obj) == 0;
    }
    else
    {
      return false;
    }
  }



  /**
   * Returns the attribute value contained in this RDN which is associated with
   * the provided attribute type, or {@code null} if this RDN does not include
   * such an attribute value.
   *
   * @param attributeType
   *          The attribute type.
   * @return The attribute value.
   */
  public ByteString getAttributeValue(final AttributeType attributeType)
  {
    for (final AVA ava : avas)
    {
      if (ava.getAttributeType().equals(attributeType))
      {
        return ava.getAttributeValue();
      }
    }
    return null;
  }



  /**
   * Returns the first AVA contained in this RDN.
   *
   * @return The first AVA contained in this RDN.
   */
  public AVA getFirstAVA()
  {
    return avas[0];
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    // Avoid an algorithm that requires the AVAs to be sorted.
    int hash = 0;
    for (final AVA ava : avas)
    {
      hash += ava.hashCode();
    }
    return hash;
  }



  /**
   * Returns {@code true} if this RDN contains more than one AVA.
   *
   * @return {@code true} if this RDN contains more than one AVA, otherwise
   *         {@code false}.
   */
  public boolean isMultiValued()
  {
    return avas.length > 1;
  }



  /**
   * Returns an iterator of the AVAs contained in this RDN. The AVAs will be
   * returned in the user provided order.
   * <p>
   * Attempts to remove AVAs using an iterator's {@code remove()} method are not
   * permitted and will result in an {@code UnsupportedOperationException} being
   * thrown.
   *
   * @return An iterator of the AVAs contained in this RDN.
   */
  public Iterator<AVA> iterator()
  {
    return Iterators.arrayIterator(avas);
  }



  /**
   * Returns the number of AVAs in this RDN.
   *
   * @return The number of AVAs in this RDN.
   */
  public int size()
  {
    return avas.length;
  }



  /**
   * Returns the RFC 4514 string representation of this RDN.
   *
   * @return The RFC 4514 string representation of this RDN.
   * @see <a href="http://tools.ietf.org/html/rfc4514">RFC 4514 - Lightweight
   *      Directory Access Protocol (LDAP): String Representation of
   *      Distinguished Names </a>
   */
  @Override
  public String toString()
  {
    // We don't care about potential race conditions here.
    if (stringValue == null)
    {
      final StringBuilder builder = new StringBuilder();
      avas[0].toString(builder);
      for (int i = 1; i < avas.length; i++)
      {
        builder.append('+');
        avas[i].toString(builder);
      }
      stringValue = builder.toString();
    }
    return stringValue;
  }



  StringBuilder toString(final StringBuilder builder)
  {
    return builder.append(toString());
  }
}
