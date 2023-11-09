
package org.eclipse.store.examples.items;

/*-
 * #%L
 * EclipseStore Example Items
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.jline.builtins.Builtins;
import org.jline.builtins.Completers.SystemCompleter;
import org.jline.builtins.Widgets.CmdDesc;
import org.jline.builtins.Widgets.CmdLine;
import org.jline.builtins.Widgets.TailTipWidgets;
import org.jline.builtins.Widgets.TailTipWidgets.TipType;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;


public class Main
{
	public static void main(final String[] args)
	{
		new Main().startCli();
	}

	private final EmbeddedStorageManager storageManager;

	private Main()
	{
		this.storageManager = EmbeddedStorage.start(
			new DataRoot(),
			Paths.get("data")
		);
	}

	/**
	 * Starts the command line interface, for commands see {@link Commands}
	 */
	@SuppressWarnings("unused")
	private void startCli()
	{
		final Path            workDir         = Paths.get("");
		final Builtins        builtins        = new Builtins(workDir, null, null);
		builtins.rename(Builtins.Command.TTOP, "top");
        builtins.alias("zle", "widget");
        builtins.alias("bindkey", "keymap");
		final SystemCompleter systemCompleter = builtins.compileCompleters();
		final CommandLine     cli             = Commands.createCommandLine(this.storageManager);
		final PicocliCommands picocliCommands = new PicocliCommands(workDir, cli);
		systemCompleter.add(picocliCommands.compileCompleters());
		systemCompleter.compile();
		Terminal terminal;
        try
		{
			terminal = TerminalBuilder.builder().build();
		}
		catch(final IOException e)
		{
			throw new IORuntimeException(e);
		}
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(systemCompleter)
                .parser(new DefaultParser())
                .variable(LineReader.LIST_MAX, 50)
                .build();
        builtins.setLineReader(reader);
        final DescriptionGenerator descriptionGenerator = new DescriptionGenerator(builtins, picocliCommands);
        new TailTipWidgets(reader, descriptionGenerator::commandDescription, 5, TipType.COMPLETER);

        cli.usage(System.out);

		final String prompt      = "Items> ";
		final String rightPrompt = null;

        while(true)
        {
        	final String line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
            if(line.matches("^\\s*#.*"))
            {
                continue;
            }
			final ParsedLine pl        = reader.getParser().parse(line, 0);
			final String[]   arguments = pl.words().toArray(new String[0]);
			final String     command   = Parser.getCommand(pl.word());
            if(builtins.hasCommand(command))
            {
                try
				{
					builtins.execute(
						command,
						Arrays.copyOfRange(arguments, 1, arguments.length),
						System.in,
						System.out,
						System.err
					);
				}
				catch(final Exception e)
				{
					throw new RuntimeException(e);
				}
            }
            else
            {
                cli.execute(arguments);
            }
        }
	}


	private static class DescriptionGenerator
	{
		Builtins        builtins;
		PicocliCommands picocli;

		public DescriptionGenerator(final Builtins builtins, final PicocliCommands picocli)
		{
			this.builtins = builtins;
			this.picocli  = picocli;
		}

		CmdDesc commandDescription(final CmdLine line)
		{
			switch(line.getDescriptionType())
			{
				case COMMAND:
					final String cmd = Parser.getCommand(line.getArgs().get(0));
					if(this.builtins.hasCommand(cmd))
					{
						return this.builtins.commandDescription(cmd);
					}
					if(this.picocli.hasCommand(cmd))
					{
						return this.picocli.commandDescription(cmd);
					}
					break;
				default:
					break;
			}
			return null;
		}
	}
	
}
