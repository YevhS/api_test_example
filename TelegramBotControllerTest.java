package mate.academy.cusbo.controller.telegram;

import static java.lang.String.format;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mate.academy.cusbo.BaseApiTest;
import mate.academy.cusbo.TelegramBotApplication;
import mate.academy.cusbo.config.CusboPostgresqlContainer;
import mate.academy.cusbo.exception.FileIsTooBigException;
import mate.academy.cusbo.model.entity.Chat;
import mate.academy.cusbo.model.entity.ChatMessage;
import mate.academy.cusbo.model.entity.ChatUser;
import mate.academy.cusbo.repository.ChatMessageRepository;
import mate.academy.cusbo.repository.ChatRepository;
import mate.academy.cusbo.repository.ChatUserRepository;
import mate.academy.cusbo.service.outbound.TelegramHttpClient;
import org.junit.ClassRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Disabled("Disabled until CI env has been updated to use aws/codebuild/standard:5.0!")
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = TelegramBotApplication.class
)
@ActiveProfiles("graphql")
@Sql(scripts = {"classpath:database/chats.sql", "classpath:database/telegram.sql"})
@AutoConfigureMockMvc
class TelegramBotControllerTest extends BaseApiTest {
    private static final long BOT_ID = 1L;
    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatUserRepository chatUserRepository;

    @ClassRule
    public static PostgreSQLContainer<CusboPostgresqlContainer> postgreSQLContainer
            = CusboPostgresqlContainer.getInstance();

    private MockMvc mockMvc;

    @MockBean
    private TelegramHttpClient telegramHttpClient;

    @BeforeEach
    void setup() {
        mockTelegramApiCalls();
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    // TODO: think how to verify the test case result. Using CrudRepository is not the best choice.
    @Test
    @DisplayName("New text message is saved to db")
    void usualMessageReceived_ok() throws Exception {
        String usualMessageEvent = "usual_message";
        String externalUserId = "245512067";

        Assertions.assertTrue(
                chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                                externalUserId,
                                BOT_ID
                        )
                        .isEmpty(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should not exist before the test case start!",
                        externalUserId,
                        BOT_ID
                )
        );

        sendRequest(usualMessageEvent);

        Optional<Chat> optionalChat = chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                externalUserId,
                BOT_ID
        );

        Assertions.assertTrue(
                optionalChat.isPresent(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should be created after the request was sent",
                        externalUserId,
                        BOT_ID
                )
        );

        Chat chat = optionalChat.get();
        Long chatId = chat.getId();

        Chat.ChatStatusName actualChatStatus = chat.getStatus();
        Chat.ChatStatusName expectedChatStatus = Chat.ChatStatusName.NEW;
        Assertions.assertEquals(expectedChatStatus, actualChatStatus,
                String.format(
                        "Chat status should be \"%s\" but it is \"%s\"",
                        expectedChatStatus,
                        actualChatStatus
                )
        );

        Chat.ChatType actualChatType = chat.getType();
        Chat.ChatType expectedChatType = Chat.ChatType.PRIVATE;
        Assertions.assertEquals(expectedChatType, actualChatType,
                String.format("Chat type should be \"%s\" but it is \"%s\"",
                        expectedChatType,
                        actualChatType
                )
        );

        String externalMessageId = "123344";
        Optional<ChatMessage> optionalChatMessage = chatMessageRepository
                .findByExternalMessageIdAndChatId(
                        externalMessageId,
                        chatId
                );

        Assertions.assertTrue(
                optionalChatMessage.isPresent()
        );

        String expectedMessageText = "Hello cusbo!";
        ChatMessage chatMessage = optionalChatMessage.get();
        String actualMessageText = chatMessage.getText();
        Assertions.assertEquals(expectedMessageText, actualMessageText,
                String.format("Message text should be \"%s\" but it is \"%s\"",
                        expectedChatType,
                        actualChatType
                )
        );

        ChatMessage.MessageType actualMessageType = chatMessage.getType();
        ChatMessage.MessageType expectedMessageType = ChatMessage.MessageType.CONVERSATION;

        Assertions.assertEquals(expectedMessageType, actualMessageType,
                String.format("Message type should be \"%s\" but it is \"%s\"",
                        expectedMessageType,
                        actualMessageType
                )
        );
    }

    @Test
    @DisplayName("New chat is created after \"/start\" command")
    void startCommandIsProcessed() throws Exception {
        String externalUserId = "411711814";
        String startCommandEvent = "start_command";

        Assertions.assertTrue(
                chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(externalUserId, BOT_ID)
                        .isEmpty(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should not exist before the test case start!",
                        externalUserId,
                        BOT_ID
                )
        );

        sendRequest(startCommandEvent);

        Optional<Chat> optionalChat = chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                externalUserId,
                BOT_ID
        );

        Assertions.assertTrue(
                optionalChat.isPresent(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should be created after the request was sent",
                        externalUserId,
                        BOT_ID
                )
        );

        Chat chat = optionalChat.get();
        Long chatId = chat.getId();

        Chat.ChatStatusName actualChatStatus = chat.getStatus();
        Chat.ChatStatusName expectedChatStatus = Chat.ChatStatusName.NEW;
        Assertions.assertEquals(expectedChatStatus, actualChatStatus,
                String.format(
                        "Chat status should be \"%s\" but it is \"%s\"",
                        expectedChatStatus,
                        actualChatStatus
                )
        );

        Chat.ChatType actualChatType = chat.getType();
        Chat.ChatType expectedChatType = Chat.ChatType.PRIVATE;
        Assertions.assertEquals(expectedChatType, actualChatType,
                String.format(
                        "Chat type should be \"%s\" but it is \"%s\"",
                        expectedChatType,
                        actualChatType
                )
        );

        String externalMessageId = "215";
        Optional<ChatMessage> optionalChatMessage = chatMessageRepository
                .findByExternalMessageIdAndChatId(
                        externalMessageId,
                        chatId
                );

        Assertions.assertTrue(
                optionalChatMessage.isPresent()
        );

        ChatMessage chatMessage = optionalChatMessage.get();

        String expectedMessageText = "/start";
        String actualMessageText = chatMessage.getText();
        Assertions.assertEquals(expectedMessageText, actualMessageText,
                String.format(
                        "Message text should be \"%s\" but it is \"%s\"",
                        expectedMessageText,
                        actualMessageText
                )
        );

        ChatMessage.MessageType actualMessageType = chatMessage.getType();
        ChatMessage.MessageType expectedMessageType = ChatMessage.MessageType.COMMAND;
        Assertions.assertEquals(expectedMessageType, actualMessageType,
                String.format(
                        "Message type should be \"%s\" but it is \"%s\"",
                        expectedMessageType,
                        actualMessageType
                )
        );
    }

    @Test
    @DisplayName("Edited message is updated in db")
    void messageIsEdited() throws Exception {
        String externalUserId = "411711813";
        String newMessageEvent = "new_message";

        Assertions.assertTrue(
                chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(externalUserId, BOT_ID)
                        .isEmpty(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should not exist before the test case start!",
                        externalUserId,
                        BOT_ID
                )
        );

        sendRequest(newMessageEvent);

        Optional<Chat> optionalChat = chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                externalUserId,
                BOT_ID
        );

        Assertions.assertTrue(
                optionalChat.isPresent(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should be created after the request was sent",
                        externalUserId,
                        BOT_ID
                )
        );

        Chat chat = optionalChat.get();
        Long chatId = chat.getId();
        String editedMessageEvent = "edited_message";

        sendRequest(editedMessageEvent);

        String externalMessageId = "8522";
        Optional<ChatMessage> optionalChatMessage = chatMessageRepository
                .findByExternalMessageIdAndChatId(
                        externalMessageId,
                        chatId
                );

        ChatMessage chatMessage = optionalChatMessage.get();
        String expectedEditedMessageText = "message edited";
        String actualEditedMessageText = chatMessage.getText();
        Assertions.assertEquals(expectedEditedMessageText, actualEditedMessageText,
                String.format(
                        "The edited message text should be \"%s\" but it is \"%s\"",
                        expectedEditedMessageText,
                        actualEditedMessageText
                )
        );
    }

    @Test
    @DisplayName("ChatUser status is updated in db after user blocks the bot")
    void botBlockedByUserEventProcessed() throws Exception {
        String externalUserId = "411711811";
        String startCommandEvent = "start_command_another_user";

        Assertions.assertTrue(
                chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(externalUserId, BOT_ID)
                        .isEmpty(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should not exist before the test case start!",
                        externalUserId,
                        BOT_ID
                )
        );

        sendRequest(startCommandEvent);

        Optional<Chat> optionalChat = chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                externalUserId,
                BOT_ID
        );

        Assertions.assertTrue(
                optionalChat.isPresent(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should be created after the request was sent",
                        externalUserId,
                        BOT_ID
                )
        );

        Chat chat = optionalChat.get();
        String botBlockedEvent = "bot_blocked";

        sendRequest(botBlockedEvent);

        Optional<ChatUser> optionalChatUser = chatUserRepository.findByChatAndUserExternalUserId(
                chat,
                externalUserId
        );

        ChatUser.ChatUserStatus actualChatUserStatus = optionalChatUser.get().getStatus();
        ChatUser.ChatUserStatus expectedChatUserStatus = ChatUser.ChatUserStatus.BLOCKED;
        Assertions.assertEquals(expectedChatUserStatus, actualChatUserStatus,
                String.format(
                        "ChatUser status should be \"%s\" but it is \"%s\"",
                        expectedChatUserStatus,
                        actualChatUserStatus
                )
        );
    }

    @Test
    @DisplayName("GIF message is saved to db")
    void gifMessageEventIsProcessed() throws Exception {
        String externalUserId = "411711812";
        String stickerMessageEvent = "sticker_message";

        Assertions.assertTrue(
                chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(externalUserId, BOT_ID)
                        .isEmpty(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should not exist before the test case start!",
                        externalUserId,
                        BOT_ID
                )
        );

        sendRequest(stickerMessageEvent);

        Optional<Chat> optionalChat = chatRepository.findChatByExternalChatIdAndBotIdWithoutRelations(
                externalUserId,
                BOT_ID
        );

        Assertions.assertTrue(
                optionalChat.isPresent(),
                String.format(
                        "Chat for externalUserId = %s and botId = %s should be created after the request was sent",
                        externalUserId,
                        BOT_ID
                )
        );

        Chat chat = optionalChat.get();
        Long chatId = chat.getId();
        String externalMessageId = "8592";

        Optional<ChatMessage> optionalChatMessage = chatMessageRepository
                .findByExternalMessageIdAndChatId(
                        externalMessageId,
                        chatId
                );

        ChatMessage chatMessage = optionalChatMessage.get();
        String expectedStickerMessageText = "sticker:🤣";
        String actualStickerMessageText = chatMessage.getText();
        Assertions.assertEquals(expectedStickerMessageText, actualStickerMessageText,
                String.format(
                        "The sticker message text should be \"%s\" but it is \"%s\"",
                        expectedStickerMessageText,
                        actualStickerMessageText
                )
        );
    }

    //TODO add a test for welcome messages
    private void sendRequest(String eventName) throws Exception {
        this.mockMvc.perform(
                        post("/tg/" + BOT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(read(format(TELEGRAM_REQUEST_PATH, eventName)))
                )
                .andDo(print())
                .andExpect(status().isOk());
    }

    private void mockTelegramApiCalls() {
        Mockito.when(telegramHttpClient.getChatDetails(any(), any()))
                .thenReturn(Map.of("result", new HashMap<>()));
        try {
            Mockito.when(telegramHttpClient.getFileInfo(any(), any())).thenReturn(Optional.empty());
        } catch (FileIsTooBigException e) {
            Assertions.fail("Can't mock telegram api client", e);
        }
    }
}
