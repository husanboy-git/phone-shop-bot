package com.test.phone.bot;

import com.test.phone.model.Role;
import com.test.phone.model.dto.PhoneDto;
import com.test.phone.model.dto.UserDto;
import com.test.phone.service.PhoneService;
import com.test.phone.service.UserService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class PhoneBot extends TelegramLongPollingBot {
    @Autowired private UserService userService;
    @Autowired private PhoneService phoneService;

    private static final String ADMIN_PASSWORD = "Java2023";  // 실제 사용할 비밀번호 설정

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            Long telegramId = update.getMessage().getFrom().getId();
            String userName = update.getMessage().getFrom().getFirstName();
            Optional<UserDto> existingUser = userService.getUserByTelegramId(telegramId);

            if(messageText.startsWith("/start")) {
                handleStartCommand(existingUser, chatId, userName, telegramId);
            } else if (messageText.startsWith("/add_admin")) {          //admin logic
                handleAddAdminCommand(chatId, messageText, userName);
            } else if (messageText.startsWith("/remove_admin")) {
                handleRemoveAdminCommand(chatId, messageText, userName);
            } else if(messageText.equals("IPHONE") || messageText.equals("SAMSUNG") || messageText.equals("OTHER")) {
                handlePhoneBrandSelection(chatId, messageText);
            } else if (messageText.startsWith("/addphone")) {
                handleAddPhoneCommand(chatId, messageText, telegramId);
            } else if (messageText.startsWith("/deletephone")) {
                handleDeletePhoneCommand(chatId, messageText, telegramId);
            }
        }
        else if (update.hasCallbackQuery()) {  // 모델 선택에 대한 callback 처리
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            handleModelSelection(chatId, callbackData);
        }
    }

    // add admin method
    private void handleAddAdminCommand(Long chatId, String messageText, String userName) {
        // /add_admin 며령어에서 비밀번호 분리
        String[] parts = messageText.split(" ");
        if(parts.length < 2) {
            sendMessage(chatId, "관리자로 추가되려면 /add_admin <비밀번호> 형식으로 입력해 주세요.");
            return;
        }

        String inputPassword = parts[1];

        if(!ADMIN_PASSWORD.equals(inputPassword)) {
            sendMessage(chatId, "잘못된 비밀번호 입니다. 관리자 권한을 얻을 수 없습니다.");
            return;
        }

        try {
            // 관리자를 추가하는 로직 호출
            userService.addAdmin(chatId, userName);
            sendMessage(chatId, "축하합니다! " + userName + "님! " + "관리로 승급하셨습니다 \uD83C\uDF89");
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    // remove admin method
    private void handleRemoveAdminCommand(Long chatId, String messageText, String userName) {
        // /remove_admin 며령어에서 비밀번호 분리
        String[] parts = messageText.split(" ");
        if(parts.length < 2) {
            sendMessage(chatId, "관리자로 제거하려면 /remove_admin <비밀번호> 형식으로 입력해 주세요.");
            return;
        }

        String inputPassword = parts[1];

        if(!ADMIN_PASSWORD.equals(inputPassword)) {
            sendMessage(chatId, "잘못된 비밀번호 입니다. 관리자 권한을 얻을 수 없습니다.");
            return;
        }

        try {
            // 관리자를 제거하는 로직 호출
            userService.removeAdmin(chatId);
            sendMessage(chatId, "관리자 권한이 성공적으로 제거되었습니다.");
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }


    private void handleStartCommand(Optional<UserDto> existingUser, Long chatId, String userName, Long telegramId) {
        if(existingUser.isPresent()) {
            sendMessage(chatId, "다시 오신 것을 환영합니다, " + userName + "님! \uD83D\uDC4B");
        } else {
            userService.addUser(telegramId, userName, Role.USER);
            sendMessage(chatId, "환영합니다, " + userName + "님! 초음 오셨군요. \uD83D\uDC4B");
        }
        showMenuButtons(chatId);
    }

    private void handlePhoneBrandSelection(Long chatId, String brand) {
        List<PhoneDto> phones = phoneService.getPhonesByBrand(brand);
        if(phones.isEmpty()) {
            sendMessage(chatId, "선택하신 브랜드에 해당하는 폰이 없습니다.");
        } else {
            /*StringBuilder phoneList = new StringBuilder("선택하신 브랜드의 폰 목록입니다:\n");
            for (PhoneDto phone : phones) {
                phoneList.append(phone.model())
                        .append(" - ").append(phone.price()).append("원\n")
                        .append("이미지: ").append(phone.image()).append("\n\n");
            }
            sendMessage(chatId, phoneList.toString());*/

            sendModelButtons(chatId, phones);  // 모델 버튼 표시
        }
    }

    private void sendModelButtons(Long chatId, List<PhoneDto> phones) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("모델을 선택해 주세요:");

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (PhoneDto phone : phones) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(phone.model());  // 모델명을 버튼 텍스트로 사용
            button.setCallbackData(phone.model());  // 모델명을 콜백 데이터로 사용
            rowInline.add(button);
            rowsInline.add(rowInline);
        }

        inlineKeyboard.setKeyboard(rowsInline);
        message.setReplyMarkup(inlineKeyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleModelSelection(Long chatId, String model) {
        List<PhoneDto> phones = phoneService.getPhonesByModel(model); // Get the list of phones by model
        if (!phones.isEmpty()) { // Check if the list is not empty
            PhoneDto selectedPhone = phones.get(0); // Get the first phone from the list
            StringBuilder phoneDetails = new StringBuilder();
            phoneDetails.append("📱 모델: ").append(selectedPhone.model()).append("\n")
                    .append("💵 가격: ").append(selectedPhone.price()).append("원\n")
                    .append("📝 상태: ").append(selectedPhone.condition()).append("\n");

            // 1. 휴대폰 세부 정보를 메시지로 전송
            sendMessage(chatId, phoneDetails.toString());

            // 2. 이미지가 있을 경우 이미지를 전송
            if (selectedPhone.image() != null && !selectedPhone.image().isEmpty()) {
                sendPhoto(chatId, selectedPhone.image());
            } else {
                sendMessage(chatId, "이미지가 없습니다.");
            }
        } else {
            sendMessage(chatId, "선택한 모델의 정보를 찾을 수 없습니다.");
        }
    }


    // 사진을 전송하는 메서드 추가
    private void sendPhoto(Long chatId, String photoUrl) {
        SendPhoto sendPhotoRequest = new SendPhoto();
        sendPhotoRequest.setChatId(chatId.toString());
        sendPhotoRequest.setPhoto(new InputFile(photoUrl)); // URL로 이미지를 전송

        try {
            execute(sendPhotoRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "이미지 전송 중 오류가 발생했습니다.");
        }
    }




    private void handleAddPhoneCommand(Long chatId, String messageText, Long telegramId) {
        Optional<UserDto> user = userService.getUserByTelegramId(telegramId);

        // 사용자가 있고, ADMIN 권한이 있는지 확인
        if(user.isPresent() && user.get().role() == Role.ADMIN) {
            String[] parts = messageText.split(" ", 6);
            // 명령어 형식: /addphone [브랜드] [모델] [가격] [이미지 URL] [조건]

            // 파라미터가 6개일 경우에만 처리
            if(parts.length == 6) {
                try {
                    String brand = parts[1];
                    String model = parts[2];
                    double price = Double.parseDouble(parts[3]); // 가격은 double 타입
                    String imageUrl = parts[4];
                    String condition = parts[5].toUpperCase(); // 'NEW' 또는 'USED'로 변환하여 처리

                    // PhoneDto 생성 후 서비스에 추가
                    PhoneDto phoneDto = new PhoneDto(null, brand, model, imageUrl, price, condition);
                    phoneService.addPhone(phoneDto);

                    sendMessage(chatId, "휴대폰이 성공적으로 추가되었습니다.");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "잘못된 가격 형식입니다. 숫자로 입력해 주세요.");
                }
            } else {
                sendMessage(chatId, "잘못된 형식입니다. 올바른 형식: /addphone [브랜드] [모델] [가격] [이미지 URL] [조건]");
            }
        } else {
            sendMessage(chatId, "이 명령어를 사용할 권한이 없습니다.");
        }
    }

    private void handleDeletePhoneCommand(Long chatId, String messageText, Long telegramId) {
        Optional<UserDto> user = userService.getUserByTelegramId(telegramId);
        if(user.isPresent() && user.get().role() == Role.ADMIN) {
            try {
                Long phoneId = Long.parseLong(messageText.split(" ")[1]);
                phoneService.deletePhone(phoneId);
                sendMessage(chatId, "휴대폰이 성공적으로 삭제되었습니다.");
            } catch (Exception e) {
                sendMessage(chatId, "휴대폰 삭제 중 오류가 발생했습니다.");
            }
        } else {
            sendMessage(chatId, "이 명령어를 사용할 권한이 없습니다.");
        }
    }

    private void showMenuButtons(Long chatId) {
        SendMessage message = new SendMessage();
        message.setText("Select a phone brand:");
        message.setChatId(chatId.toString());

        ReplyKeyboardMarkup replyKeyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        //First row of buttons: IPhone, Samsung, Other
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("IPHONE"));
        row.add(new KeyboardButton("SAMSUNG"));
        row.add(new KeyboardButton("OTHER"));

        keyboard.add(row);
        replyKeyboard.setKeyboard(keyboard);
        replyKeyboard.setResizeKeyboard(true);
        message.setReplyMarkup(replyKeyboard);

        try{
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}
