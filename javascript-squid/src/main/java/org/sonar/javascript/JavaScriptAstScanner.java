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
package org.sonar.javascript;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.AstNodeType;
import com.sonar.sslr.api.CommentAnalyser;
import com.sonar.sslr.impl.Parser;
import com.sonar.sslr.squid.*;
import com.sonar.sslr.squid.metrics.*;
import org.sonar.javascript.api.EcmaScriptGrammar;
import org.sonar.javascript.api.EcmaScriptMetric;
import org.sonar.javascript.api.EcmaScriptPunctuator;
import org.sonar.javascript.parser.EcmaScriptParser;
import org.sonar.squid.api.SourceCode;
import org.sonar.squid.api.SourceFile;
import org.sonar.squid.api.SourceFunction;
import org.sonar.squid.api.SourceProject;
import org.sonar.squid.indexer.QueryByType;

import java.io.File;
import java.util.Collection;

public final class JavaScriptAstScanner {

  private JavaScriptAstScanner() {
  }

  /**
   * Helper method for testing checks without having to deploy them on a Sonar instance.
   */
  public static SourceFile scanSingleFile(File file, SquidAstVisitor<EcmaScriptGrammar>... visitors) {
    if (!file.isFile()) {
      throw new IllegalArgumentException("File '" + file + "' not found.");
    }
    AstScanner<EcmaScriptGrammar> scanner = create(new EcmaScriptConfiguration(), visitors);
    scanner.scanFile(file);
    Collection<SourceCode> sources = scanner.getIndex().search(new QueryByType(SourceFile.class));
    if (sources.size() != 1) {
      throw new IllegalStateException("Only one SourceFile was expected whereas " + sources.size() + " has been returned.");
    }
    return (SourceFile) sources.iterator().next();
  }

  public static AstScanner<EcmaScriptGrammar> create(EcmaScriptConfiguration conf, SquidAstVisitor<EcmaScriptGrammar>... visitors) {
    final SquidAstVisitorContextImpl<EcmaScriptGrammar> context = new SquidAstVisitorContextImpl<EcmaScriptGrammar>(new SourceProject("JavaScript Project"));
    final Parser<EcmaScriptGrammar> parser = EcmaScriptParser.create(conf);

    AstScanner.Builder<EcmaScriptGrammar> builder = AstScanner.<EcmaScriptGrammar> builder(context).setBaseParser(parser);

    /* Metrics */
    builder.withMetrics(EcmaScriptMetric.values());

    /* Comments */
    builder.setCommentAnalyser(
        new CommentAnalyser() {
          @Override
          public boolean isBlank(String line) {
            for (int i = 0; i < line.length(); i++) {
              if (Character.isLetterOrDigit(line.charAt(i))) {
                return false;
              }
            }
            return true;
          }

          @Override
          public String getContents(String comment) {
            return comment.startsWith("//") ? comment.substring(2) : comment.substring(2, comment.length() - 2);
          }
        });

    /* Files */
    builder.setFilesMetric(EcmaScriptMetric.FILES);

    /* Functions */
    builder.withSquidAstVisitor(new SourceCodeBuilderVisitor<EcmaScriptGrammar>(new SourceCodeBuilderCallback() {
      public SourceCode createSourceCode(SourceCode parentSourceCode, AstNode astNode) {
        String functionName = astNode.getChild(1).getTokenValue();
        SourceFunction function = new SourceFunction(functionName + ":" + astNode.getToken().getLine());
        function.setStartAtLine(astNode.getTokenLine());
        return function;
      }
    }, parser.getGrammar().functionDeclaration, parser.getGrammar().functionExpression));

    builder.withSquidAstVisitor(CounterVisitor.<EcmaScriptGrammar> builder()
        .setMetricDef(EcmaScriptMetric.FUNCTIONS)
        .subscribeTo(parser.getGrammar().functionDeclaration, parser.getGrammar().functionExpression)
        .build());

    /* Metrics */
    builder.withSquidAstVisitor(new LinesVisitor<EcmaScriptGrammar>(EcmaScriptMetric.LINES));
    builder.withSquidAstVisitor(new LinesOfCodeVisitor<EcmaScriptGrammar>(EcmaScriptMetric.LINES_OF_CODE));
    builder.withSquidAstVisitor(CommentsVisitor.<EcmaScriptGrammar> builder().withCommentMetric(EcmaScriptMetric.COMMENT_LINES)
        .withBlankCommentMetric(EcmaScriptMetric.COMMENT_BLANK_LINES)
        .withNoSonar(true)
        .withIgnoreHeaderComment(conf.getIgnoreHeaderComments())
        .build());
    builder.withSquidAstVisitor(CounterVisitor.<EcmaScriptGrammar> builder()
        .setMetricDef(EcmaScriptMetric.STATEMENTS)
        .subscribeTo(
            parser.getGrammar().variableStatement,
            parser.getGrammar().emptyStatement,
            parser.getGrammar().labelledStatement,
            parser.getGrammar().expressionStatement,
            parser.getGrammar().ifStatement,
            parser.getGrammar().iterationStatement,
            parser.getGrammar().continueStatement,
            parser.getGrammar().breakStatement,
            parser.getGrammar().returnStatement,
            parser.getGrammar().withStatement,
            parser.getGrammar().switchStatement,
            parser.getGrammar().throwStatement,
            parser.getGrammar().tryStatement,
            parser.getGrammar().debuggerStatement)
        .build());

    AstNodeType[] complexityAstNodeType = new AstNodeType[] {
      // Entry points
      parser.getGrammar().functionDeclaration,
      parser.getGrammar().functionExpression,

      // Branching nodes
      parser.getGrammar().ifStatement,
      parser.getGrammar().iterationStatement,
      parser.getGrammar().switchStatement,
      parser.getGrammar().caseClause,
      parser.getGrammar().defaultClause,
      parser.getGrammar().catch_,
      parser.getGrammar().returnStatement,
      parser.getGrammar().throwStatement,

      // Expressions
      EcmaScriptPunctuator.QUERY,
      EcmaScriptPunctuator.ANDAND,
      EcmaScriptPunctuator.OROR
    };
    builder.withSquidAstVisitor(ComplexityVisitor.<EcmaScriptGrammar> builder()
        .setMetricDef(EcmaScriptMetric.COMPLEXITY)
        .subscribeTo(complexityAstNodeType)
        .build());

    /* External visitors (typically Check ones) */
    for (SquidAstVisitor<EcmaScriptGrammar> visitor : visitors) {
      builder.withSquidAstVisitor(visitor);
    }

    return builder.build();
  }

}
