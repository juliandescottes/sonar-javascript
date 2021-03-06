/*
 * Sonar JavaScript Plugin
 * Copyright (C) 2011 Eriks Nukis and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.javascript.parser.grammar.statements;

import com.sonar.sslr.impl.Parser;
import org.junit.Before;
import org.junit.Test;
import org.sonar.javascript.api.EcmaScriptGrammar;
import org.sonar.javascript.parser.EcmaScriptParser;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

public class IterationStatementTest {

  Parser<EcmaScriptGrammar> p = EcmaScriptParser.create();
  EcmaScriptGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.iterationStatement);
  }

  @Test
  public void ok() {
    g.doWhileStatement.mock();
    g.whileStatement.mock();
    g.forInStatement.mock();
    g.forStatement.mock();

    assertThat(p, parse("doWhileStatement"));
    assertThat(p, parse("whileStatement"));
    assertThat(p, parse("forInStatement"));
    assertThat(p, parse("forStatement"));
  }

  @Test
  public void realLife() {
    assertThat(p, parse("do { } while (a < b);"));
    assertThat(p, parse("while (a < b) ;"));
    assertThat(p, parse("for (n = 0; n < h; n++) ;"));
  }

}
