/*******************************************************************************
 * Copyright (c) 2009, 2018 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Filters out instructions that were inlined by Kotlin compiler.
 */
public final class KotlinInlineFilter implements IFilter {

	public void filter(final MethodNode methodNode,
			final IFilterContext context, final IFilterOutput output) {
		if (context.getSourceDebugExtension() == null) {
			return;
		}
		if (!context.getClassAnnotations()
				.contains(KotlinGeneratedFilter.KOTLIN_METADATA_DESC)) {
			return;
		}

		final int max = parseSMAP(context.getSourceDebugExtension());
		int line = 0;
		for (AbstractInsnNode i = methodNode.instructions
				.getFirst(); i != null; i = i.getNext()) {
			if (AbstractInsnNode.LINE == i.getType()) {
				line = ((LineNumberNode) i).line;
			}
			if (line >= max) {
				output.ignore(i, i);
			}
		}
	}

	private static int parseSMAP(final String smap) {
		try {
			final BufferedReader br = new BufferedReader(
					new StringReader(smap));
			readLine(br, "SMAP");
			// OutputFileName
			br.readLine();
			// DefaultStratumId
			readLine(br, "Kotlin");
			// StratumSection
			readLine(br, "*S Kotlin");
			// FileSection
			readLine(br, "*F");
			while (!"*L".equals(br.readLine())) {
			}
			// LineSection
			while (!"*E".equals(br.readLine())) {
			}
			// StratumSection
			readLine(br, "*S KotlinDebug");
			// FileSection
			readLine(br, "*F");
			while (!"*L".equals(br.readLine())) {
			}
			return parseLineSection(br);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void readLine(final BufferedReader br, final String expected)
			throws IOException {
		if (!expected.equals(br.readLine())) {
			throw new AssertionError();
		}
	}

	private static int parseLineSection(final BufferedReader br)
			throws IOException {
		int min = Integer.MAX_VALUE;
		String line;
		while (!"*E".equals(line = br.readLine())) {
			min = Math.min(parseLineInfo(line), min);
		}
		return min;
	}

	private static int parseLineInfo(final String input) {
		final Matcher m = LINE_INFO_PATTERN.matcher(input);
		if (!m.matches()) {
			throw new AssertionError();
		}
		return Integer.parseInt(m.group(4).substring(1));
	}

	private static final Pattern LINE_INFO_PATTERN = Pattern.compile("" //
			+ "([0-9]++)" // InputStartLine
			+ "(#[0-9]++)?+" // LineFileID
			+ "(,[0-9]++)?+" // RepeatCount
			+ "(:[0-9]++)" // OutputStartLine
			+ "(,[0-9]++)?+" // OutputLineIncrement
	);

}
