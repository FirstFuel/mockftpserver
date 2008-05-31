/*
 * Copyright 2008 the original author or authors.
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
package org.mockftpserver.fake.command

import org.mockftpserver.core.CommandSyntaxException
import org.mockftpserver.core.IllegalStateException
import org.mockftpserver.core.NotLoggedInException
import org.mockftpserver.core.command.Command
import org.mockftpserver.core.command.ReplyCodes
import org.mockftpserver.core.session.Session
import org.mockftpserver.core.session.SessionKeys
import org.mockftpserver.core.session.StubSession
import org.mockftpserver.fake.StubServerConfiguration
import org.mockftpserver.fake.filesystem.ExistingFileOperationException
import org.mockftpserver.fake.filesystem.FakeUnixFileSystem
import org.mockftpserver.fake.filesystem.FileSystemException
import org.mockftpserver.fake.filesystem.InvalidFilenameException
import org.mockftpserver.fake.filesystem.NewFileOperationException
import org.mockftpserver.test.AbstractGroovyTest

/**
 * Tests for AbstractFakeCommandHandler
 *
 * @version $Revision$ - $Date$
 *
 * @author Chris Mair
 */
class AbstractFakeCommandHandlerClassTest extends AbstractGroovyTest {

    static PATH = "some/path"
    static REPLY_CODE = 99
    static ARG = "ABC"
    static MSG = "text {0}"
    static MSG_WITH_ARG = "text ABC"
    private AbstractFakeCommandHandler commandHandler
    private session
    private serverConfiguration
    private fileSystem

    //-------------------------------------------------------------------------
    // Tests
    //-------------------------------------------------------------------------

    void testHandleCommand() {
        def command = new Command("C1", ["abc"])
        commandHandler.handleCommand(command, session)
        assert commandHandler.handled

        assertHandleCommandReplyCode(new CommandSyntaxException(""), ReplyCodes.COMMAND_SYNTAX_ERROR)
        assertHandleCommandReplyCode(new IllegalStateException(""), ReplyCodes.ILLEGAL_STATE)
        assertHandleCommandReplyCode(new NotLoggedInException(""), ReplyCodes.NOT_LOGGED_IN)
        assertHandleCommandReplyCode(new ExistingFileOperationException(""), ReplyCodes.EXISTING_FILE_ERROR)
        assertHandleCommandReplyCode(new NewFileOperationException(""), ReplyCodes.NEW_FILE_ERROR)
        assertHandleCommandReplyCode(new InvalidFilenameException(""), ReplyCodes.FILENAME_NOT_VALID)

        shouldFail { commandHandler.handleCommand(null, session) }
        shouldFail { commandHandler.handleCommand(command, null) }
    }

    void testHandleCommand_FileSystemException() {
        assertHandleCommandReplyCode(new FileSystemException(PATH), ReplyCodes.EXISTING_FILE_ERROR, PATH)
        commandHandler.replyCodeForFileSystemException = ReplyCodes.NEW_FILE_ERROR
        assertHandleCommandReplyCode(new FileSystemException(PATH), ReplyCodes.NEW_FILE_ERROR, PATH)
    }

    void testSendReply() {
        commandHandler.sendReply(session, REPLY_CODE)
        assert session.sentReplies[0] == [REPLY_CODE, MSG], session.sentReplies[0]

        commandHandler.sendReply(session, REPLY_CODE, [ARG])
        assert session.sentReplies[1] == [REPLY_CODE, MSG_WITH_ARG], session.sentReplies[0]

        shouldFail { commandHandler.sendReply(null, REPLY_CODE) }
        shouldFail { commandHandler.sendReply(session, 0) }
    }

    void testAssertValidReplyCode() {
        commandHandler.assertValidReplyCode(1)        // no exception expected
        shouldFail { commandHandler.assertValidReplyCode(0) }
    }

    void testGetRequiredParameter() {
        def command = new Command("C1", ["abc"])
        assert commandHandler.getRequiredParameter(command) == "abc"
        assert commandHandler.getRequiredParameter(command, 0) == "abc"
        shouldFail(CommandSyntaxException) { commandHandler.getRequiredParameter(command, 1) }

        command = new Command("C1", [])
        shouldFail(CommandSyntaxException) { commandHandler.getRequiredParameter(command) }
    }

    void testGetRequiredSessionAttribute() {
        shouldFail(IllegalStateException) { commandHandler.getRequiredSessionAttribute(session, "undefined") }

        session.setAttribute("abc", "not empty")
        commandHandler.getRequiredSessionAttribute(session, "abc") // no exception

        session.setAttribute("abc", "")
        commandHandler.getRequiredSessionAttribute(session, "abc") // no exception
    }

    void testVerifyLoggedIn() {
        shouldFail(NotLoggedInException) { commandHandler.verifyLoggedIn(session) }
        session.setAttribute(SessionKeys.USER_ACCOUNT, "OK")
        commandHandler.verifyLoggedIn(session)        // no exception expected
    }

    void testVerifyFileSystemCondition() {
        commandHandler.verifyFileSystemCondition(true, PATH)    // no exception expected
        shouldFail(FileSystemException) { commandHandler.verifyFileSystemCondition(false, PATH) }
        shouldFail(FileSystemException) { commandHandler.verifyFileSystemCondition([], PATH) }
    }

    void testGetRealPath() {
        assert commandHandler.getRealPath(session, "/xxx") == "/xxx"

        session.setAttribute(SessionKeys.CURRENT_DIRECTORY, "/usr/me")
        assert commandHandler.getRealPath(session, null) == "/usr/me"
        assert commandHandler.getRealPath(session, "/xxx") == "/xxx"
        assert commandHandler.getRealPath(session, "xxx") == "/usr/me/xxx"
    }

    //-------------------------------------------------------------------------
    // Test Setup
    //-------------------------------------------------------------------------

    void setUp() {
        super.setUp()
        commandHandler = new TestFakeCommandHandler()
        session = new StubSession()
        serverConfiguration = new StubServerConfiguration()
        fileSystem = new FakeUnixFileSystem()
        serverConfiguration.setFileSystem(fileSystem)

        serverConfiguration.setTextForReplyCode(REPLY_CODE, MSG)

        commandHandler.serverConfiguration = serverConfiguration
    }

    //-------------------------------------------------------------------------
    // Helper Methods
    //-------------------------------------------------------------------------

    /**
     * Assert that when the CommandHandler handleCommand() method throws the
     * specified exception, that the expected reply is sent through the session.
     */
    private void assertHandleCommandReplyCode(Throwable exception, int expected, text = null) {
        commandHandler.exception = exception
        def command = new Command("C1", ["abc"])
        session.sentReplies.clear()
        commandHandler.handleCommand(command, session)
        def sentReply = session.sentReplies[0][0]
        assert sentReply == expected
        if (text) {
            def sentMessage = session.sentReplies[0][1]
            assert sentMessage.contains(text), "sentMessage=[$sentMessage] text=[$text]"
        }
    }

}

/**
 * Concrete subclass of AbstractFakeCommandHandler for testing
 */
private class TestFakeCommandHandler extends AbstractFakeCommandHandler {
    boolean handled = false
    def exception

    protected void handle(Command command, Session session) {
        if (exception) {
            throw exception
        }
        this.handled = true
    }
}