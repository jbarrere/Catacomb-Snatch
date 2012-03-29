package com.mojang.mojam.console;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

import com.mojang.mojam.MojamComponent;
import com.mojang.mojam.Console.Command;
import com.mojang.mojam.console.commands.Chat;
import com.mojang.mojam.console.commands.Cooldown;
import com.mojang.mojam.console.commands.Exit;
import com.mojang.mojam.console.commands.Give;
import com.mojang.mojam.console.commands.Help;
import com.mojang.mojam.console.commands.Lang;
import com.mojang.mojam.console.commands.Load;
import com.mojang.mojam.console.commands.Menu;
import com.mojang.mojam.console.commands.Pause;
import com.mojang.mojam.console.commands.Time;
import com.mojang.mojam.gui.components.Font;
import com.mojang.mojam.network.kryo.Network.ConsoleMessage;
import com.mojang.mojam.screen.AbstractScreen;

public class Console implements KeyListener {

    /***
     * Maximum amount of verbose data kept in the console also the number of
     * lines of data displayed
     */
    public static final int MAX_LINES = 20;

    /***
     * Maximum number of characters allowed to input into the console
     */
    public static final int MAX_INPUT_LENGTH = 60;

    private ArrayList<String> verboseData = new ArrayList<String>(MAX_LINES);

    private String typing = "";
    private String input = null;
    private boolean completedInput;

    private boolean open;

    private boolean ingame = false;

    /***
     * Left padding size when drawing console text
     */
    public static final int xOffset = 5;

    /***
     * Top padding size when drawing console text. affects console height
     */
    public static final int yOffset = 5;

    public Console() {
	log("------------------------------------------------------------");// Deep
									    // magic
									    // lining
									    // it
									    // up
	log("|Catacomb Snatch Console v1.1                           |");
	log("|Type commands with a slash in front, like /this        |");
	log("|If in doubt, type /help                                  |");
	log("------------------------------------------------------------");
	log("");
    }

    /***
     * Logs the verbose info into the console
     * 
     * @param s
     *            information to display in console
     */
    public void log(String s) {
	if (s == null)
	    return;

	if (verboseData.size() + 1 > MAX_LINES)
	    verboseData.remove(verboseData.size() - 1);

	verboseData.add(0, s);
    }

    /***
     * Closes the console and cancels current input
     */
    private void close() {
	typing = "";
	input = null;
	completedInput = false;
	open = false;
    }

    /***
     * Opens the console
     */
    private void open() {
	open = true;
    }

    /***
     * Toggles between open and close.
     */
    public void toggle(boolean inGame) {
	if (open)
	    close();
	else
	    open();

	ingame = inGame;
    }

    /***
     * Toggles between open and close.
     */
    public void toggle() {
	if (open)
	    close();
	else
	    open();
    }

    /***
     * Tells if the console is open or not
     * 
     * @return the answer
     */
    public boolean isOpen() {
	return open;
    }

    /***
     * renders the console on the screen if it is open screen space it takes up
     * is (MAX_LINES+1) * Font.FONT_WHITE_SMALL + yOffset
     * 
     * @param s
     *            screen to render to
     */
    public void render(AbstractScreen s) {
	if (open) {
	    int fontHeight = Font.FONT_WHITE_SMALL.getFontHeight();
	    int consoleHeight = (MAX_LINES + 1) * fontHeight + yOffset; // +1
									// for
									// the
									// input
									// line

	    s.alphaFill(0, 0, s.getWidth(), consoleHeight, 0xff000000, 0x80); // 50%
									      // black,fixed
									      // from
									      // 0x50
									      // (31.25%)

	    Font.FONT_WHITE_SMALL
		    .draw(s,
			    typing
				    + (((((int) (System.currentTimeMillis() / 500)) & 1) == 1) ? "|"
					    : ""), xOffset,
			    (consoleHeight -= fontHeight)); // draws bottom up
							    // starting with
							    // typing

	    for (int i = 0; i < verboseData.size(); i++) {
		Font.FONT_WHITE_SMALL.draw(s, verboseData.get(i), xOffset,
			(consoleHeight -= fontHeight)); // and then the verbose
							// data in order of
							// newest first
	    }
	}
    }

    /***
     * checks if the user has inputed anything unnecessary if the console is
     * closed
     */
    public void tick() {
	if (completedInput) {
	    processInput(input);
	}
    }

    private void processInput(String input) {
	log(">" + input);
	String cleanInput = scrubInput(input);

	if (cleanInput.startsWith("/")) {
	    Command command = findCommand(cleanInput, input);
	    if (null != command) {
		if (command.isSendToClients()) {
		    // send message to other client(s)
		    MojamComponent.instance.synchronizer
			    .addMessage(new ConsoleMessage(input));
		}

		command.execute();
	    } else {
		log("ERROR: Command " + input
			+ " Not found! try /help for a list of commands.");
	    }
	} else {
	    chat.execute(new String[] { input });
	}

	completedInput = false;
    }

    public void processInputFromNetwork(String input) { // separate processor so
							// message is not resent
	String command = scrubInput(input);
	if (command.startsWith("/")) {
	    findCommand(command, input).execute();
	}
    }

    private String scrubInput(String input) {
	input = input.toLowerCase();
	if (!input.contains(" ")) {
	    return input;
	} else {
	    return input.substring(0, input.indexOf(' '));
	}
    }

    /***
     * Execute a console command if no command has that name nothing will be
     * done
     * 
     * @param command
     *            command name
     * @param input
     *            arguments for the command separated by spaces
     */
    public Command findCommand(String command, String input) {
	if (command.charAt(0) == '/')
	    command = command.substring(1); // remove forward slash

	for (Command c : Command.commands) {

	    if (c != null && c.name.equals(command)) {

		String[] args = getArgs(input, c.numberOfArgs);
		c.args = args;
		return c;
	    }
	}
	return null;
    }

    private String[] getArgs(String input, int numberOfArgs) {
	if (numberOfArgs == -1) { // see Command NumberOfArgs for reason
	    if (!input.contains(" ")) {
		return new String[] { "" };
	    } else {
		return new String[] { removeCommand(input) };
	    }
	}

	if (numberOfArgs <= 0)
	    return null;

	String[] args = new String[numberOfArgs];
	input = removeCommand(input);
	if (numberOfArgs == 1)
	    return new String[] { input };

	for (int i = 0; i < numberOfArgs; i++) {
	    int index = input.indexOf(' ');

	    if (index > 0) {
		args[i] = input.substring(0, index);
		input = input.substring(index + 1);
	    }
	}
	return args;
    }

    private String removeCommand(String input) {
	if (input.charAt(0) != '/') {
	    return input;
	}
	if (!input.contains(" ")) {
	    return input;
	}
	return input.substring(input.indexOf(' ') + 1);
    }

    public void keyTyped(KeyEvent e) {
	if (open) {
	    switch (e.getKeyCode()) {
	    case KeyEvent.VK_ESCAPE:
	    case KeyEvent.VK_ENTER:
		break;
	    case KeyEvent.VK_BACK_SPACE:
		if (typing.length() > 0)
		    typing = typing.substring(0, typing.length() - 1);
		break;
	    default:
		if (typing.length() < MAX_INPUT_LENGTH)
		    typing += e.getKeyChar();
		break;
	    }
	}
    }

    public void keyPressed(KeyEvent e) {
	if (open) {
	    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
		if (typing.length() > 0)
		    typing = typing.substring(0, typing.length() - 1);
	    }
	}
    }

    public void keyReleased(KeyEvent e) {
	if (open) {
	    switch (e.getKeyCode()) {
	    case KeyEvent.VK_ESCAPE:
		typing = "";
		input = null;
		break;
	    case KeyEvent.VK_ENTER:
		typing = typing.trim();
		if (!typing.equals("")) {
		    input = typing;
		    completedInput = true;
		}
		typing = "";
		break;
	    case KeyEvent.VK_BACK_SPACE:
		if (typing.length() > 0)
		    typing = typing.substring(0, typing.length() - 1);
		break;
	    }
	}
    }

    /***
     * List of possible commands
     */
    public Help help = new Help();

    public Pause pause = new Pause();

    public Exit exit = new Exit();

    public Chat chat = new Chat();

    public Load load = new Load();

    public Lang lang = new Lang();

    public Menu menu = new Menu();

    public Give give = new Give();

    public Time time = new Time();

    public Cooldown cool = new Cooldown();

    public abstract static class Command {

	public String name;
	public String helpMessage;
	public int numberOfArgs; // -1 args means return raw input data minus
				 // the command
	public static ArrayList<Command> commands = new ArrayList<Command>();
	public String[] args;
	private boolean sendToClients;

	public boolean isSendToClients() {
	    return sendToClients;
	}

	public Command(String name, int numberOfArgs, String helpMessage,
		boolean sendToClients) {
	    this.name = name;
	    this.numberOfArgs = numberOfArgs;
	    this.helpMessage = helpMessage;
	    commands.add(this);
	    this.sendToClients = sendToClients;
	}

	public abstract boolean canRunInGame();

	public abstract boolean canRunInMenu();

	public void log(String s) {
	    MojamComponent.instance.console.log(s);
	}

	public void execute(String[] args) {
	    this.args = args;
	    execute();
	}
	
	public abstract void execute();
    }

}