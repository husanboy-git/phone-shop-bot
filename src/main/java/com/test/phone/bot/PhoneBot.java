package com.test.phone.bot;

import com.test.phone.model.Role;
import com.test.phone.model.dto.PhoneDto;
import com.test.phone.model.dto.UserDto;
import com.test.phone.model.entity.PhoneEntity;
import com.test.phone.service.PhoneService;
import com.test.phone.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;

@Component
public class PhoneBot extends TelegramLongPollingBot {
    @Autowired private UserService userService;
    @Autowired private PhoneService phoneService;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    private final Map<Long, String> userState = new HashMap<>(); //사용자의 상태 관리
    private final Map<Long, PhoneEntity> phoneDataBuffer = new HashMap<>(); // 폰 데이터 임시 저장 (사진과 정보를 함께 저장)

    private static final String WAITING_FOR_BRAND = "WAITING_FOR_BRAND";
    private static final String WAITING_FOR_MODEL = "WAITING_FOR_MODEL";
    private static final String WAITING_FOR_PRICE = "WAITING_FOR_PRICE";
    private static final String WAITING_FOR_CONDITION = "WAITING_FOR_CONDITION";
    private static final String WAITING_FOR_PHOTO = "WAITING_FOR_PHOTO";

    private static final String WAITING_FOR_ADD_ADMIN = "WAITING_FOR_ADD_ADMIN";
    private static final String WAITING_FOR_REMOVE_ADMIN = "WAITING_FOR_REMOVE_ADMIN";

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            if(update.getMessage().hasPhoto()) {
                handlePhotoUpload(chatId, update);
            } else if(update.getMessage().hasText()){
                String messageText = update.getMessage().getText();
                Long telegramId = update.getMessage().getFrom().getId();
                String userName = update.getMessage().getFrom().getFirstName();
                Optional<UserDto> existingUser = userService.getUserByTelegramId(telegramId);

                if(messageText.startsWith("/start")) {
                    handleStartCommand(existingUser, chatId, userName, telegramId);
                } else if (messageText.startsWith("/add_admin")) {          //admin logic
                    handleAddAdminCommand(chatId);
                } else if (messageText.startsWith("/remove_admin")) {
                    handleRemoveAdminCommand(chatId, messageText);
                } else if(messageText.equals("IPHONE") || messageText.equals("SAMSUNG") || messageText.equals("OTHER")) {
                    handlePhoneBrandSelection(chatId, messageText);
                } else {
                    String state = userState.get(chatId);
                    if (WAITING_FOR_ADD_ADMIN.equals(state)) { // 비밀번호 입력 대기 중인 경우
                        verifyAddAdmin(chatId, messageText, userName);
                    } else if(WAITING_FOR_REMOVE_ADMIN.equals(state)) {
                        verifyRemoveAdmin(chatId, messageText, userName);
                    }
                }
                handleMessage(chatId, messageText, telegramId);
            }
        } else if (update.hasCallbackQuery()) {  // 모델 선택에 대한 callback 처리
            String callbackData = update.getCallbackQuery().getData();
            Long callbackChatId = update.getCallbackQuery().getMessage().getChatId();
            handleModelSelection(callbackChatId, callbackData);
            if ("edit_phone".equals(callbackData)) {
                handleEditPhoneCommand(callbackChatId);
            } else if ("delete_phone".equals(callbackData)) {
                handleDeletePhoneCommand(callbackChatId);
            }
        }
    }

    private void verifyAddAdmin(Long chatId, String inputPassword, String userName) {
        if(adminPassword.equals(inputPassword)) {
            try {
                // 관리자 권한 부여 로직 호출
                userService.addAdmin(chatId, userName);
                sendMessage(chatId, "축하합니다! " + userName + "님이 관리자로 추가되었습니다.");
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, e.getMessage());
            }
        } else {
            sendMessage(chatId, "잘못된 비밀번호입니다. 다시 시도해 주세요.");
        }
        userState.remove(chatId); // 상태 초기화
    }

    private void verifyRemoveAdmin(Long chatId, String inputPassword, String userName) {
        if (adminPassword.equals(inputPassword)) {
            try {
                userService.removeAdmin(chatId);
                sendMessage(chatId, "괸리자 " + userName + "님이 성공적으로 삭제되었습니다!");
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, e.getMessage());
            }
        } else {
            sendMessage(chatId, "잘못된 비밀번호입니다. 다시 시도해 주세요.");
        }
        userState.remove(chatId);
    }

    private void handleMessage(Long chatId, String messageText, Long telegramId) {
        Optional<UserDto> user = userService.getUserByTelegramId(telegramId);
        String state = userState.getOrDefault(chatId, "");
        if(user.isPresent() && user.get().role() == Role.ADMIN) {
            switch (state) {
                case WAITING_FOR_BRAND -> {
                    phoneDataBuffer.put(chatId, new PhoneEntity());
                    phoneDataBuffer.get(chatId).setBrand(messageText);
                    userState.put(chatId, WAITING_FOR_MODEL);
                    sendMessage(chatId, "모델명을 입력해 주세요:");
                }
                case WAITING_FOR_MODEL -> {
                    phoneDataBuffer.get(chatId).setModel(messageText);
                    userState.put(chatId, WAITING_FOR_PRICE);
                    sendMessage(chatId, "가격을 입력해 주세요:");
                }
                case WAITING_FOR_PRICE -> {
                    try {
                        phoneDataBuffer.get(chatId).setPrice(Double.parseDouble(messageText));
                        userState.put(chatId, WAITING_FOR_CONDITION);
                        sendMessage(chatId, "상태를 입력해 주세요 (예: 새 제품, 중고):");
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "유효한 숫자를 입력해 주세요.");
                    }
                }
                case WAITING_FOR_CONDITION -> {
                    phoneDataBuffer.get(chatId).setCondition(messageText);
                    userState.put(chatId, WAITING_FOR_PHOTO);
                    sendMessage(chatId, "휴대폰의 사진을 업로드해 주세요:");
                }
                default -> {
                    if ("/addphone".equals(messageText)) {
                        userState.put(chatId, WAITING_FOR_BRAND);
                        sendMessage(chatId, "브랜드를 입력해 주세요:");
                    }
                }
            }
        }
    }


    private void handlePhotoUpload(Long chatId, Update update) {
        if (WAITING_FOR_PHOTO.equals(userState.get(chatId))) {
            try {
                // 가장 큰 사진 파일을 선택
                String fileId = update.getMessage().getPhoto().stream()
                        .max(Comparator.comparing(photoSize -> photoSize.getFileSize()))
                        .get().getFileId();
                String filePath = getFilePath(fileId);  // getFilePath 메서드를 통해 파일 경로 획득
                File imageFile = downloadAndCompressImage(filePath);  // 텔레그램 서버에서 파일 다운로드

                // 이전에 입력된 폰 정보를 가져옴
                PhoneEntity phoneEntity = phoneDataBuffer.get(chatId);

                // PhoneService를 통해 폰 정보와 사진 파일을 함께 저장
                phoneService.addPhone(PhoneDto.toDto(phoneEntity), imageFile);
                sendMessage(chatId, "휴대폰이 성공적으로 추가되었습니다!");

                // 상태 초기화
                phoneDataBuffer.remove(chatId);
                userState.remove(chatId);
            } catch (Exception e) {
                e.printStackTrace();
                sendMessage(chatId, "사진 처리 중 오류가 발생했습니다.");
            }
        }
    }


    // 수정 명령 처리
    private void handleEditPhoneCommand(Long chatId) {
        sendMessage(chatId, "수정할 휴대폰의 새로운 정보를 입력해 주세요. 형식: [브랜드] [모델] [가격] [상태]");
        userState.put(chatId, "WAITING_FOR_EDIT");
    }

    // 삭제 명령 처리
    private void handleDeletePhoneCommand(Long chatId) {
        sendMessage(chatId, "휴대폰이 성공적으로 삭제되었습니다.");
        phoneService.deletePhone(chatId);
        userState.remove(chatId);
    }

    private String getFilePath(String fileId) throws TelegramApiException {
        return execute(new GetFile(fileId)).getFilePath();
    }

    private File downloadAndCompressImage(String filePath) throws IOException, URISyntaxException {
        String fileUrl = "https://api.telegram.org/file/bot" + token + "/" + filePath;
        URI uri = new URI(fileUrl);
        URL url = uri.toURL();

        // 이미지를 다운로드
        BufferedImage image = ImageIO.read(url);

        // 저장 경로를 D 드라이브로 설정
        File imagesDir = new File("D:/images");
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
        }

        // 파일 이름 설정
        File compressedImageFile = new File(imagesDir, UUID.randomUUID() + ".jpg");

        // 압축 품질 설정 (0.0 ~ 1.0, 1.0이 최고 품질)
        try (FileOutputStream fos = new FileOutputStream(compressedImageFile);
             ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(1.0f); // 최고 품질
            }

            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
        }

        return compressedImageFile;
    }

    // add admin method
    private void handleAddAdminCommand(Long chatId) {
        userState.put(chatId, WAITING_FOR_ADD_ADMIN);
        sendMessage(chatId, "관리자 비밀번호를 입력해 주세요:");
    }

    // remove admin method
    private void handleRemoveAdminCommand(Long chatId, String messageText) {
        userState.put(chatId, WAITING_FOR_REMOVE_ADMIN);
        sendMessage(chatId, "관리자 비밀번호를 입력해 주세요:");
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
            sendModelButtons(chatId, phones);  // 모델 버튼 표시
        }
    }

    private void sendModelButtons(Long chatId, List<PhoneDto> phones) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("모델을 선택해 주세요:");

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        Set<String> addedModels = new HashSet<>();  // 중복 모델 체크를 위한 Set

        for (PhoneDto phone : phones) {
            if (!addedModels.contains(phone.model())) {  // 이미 추가된 모델은 건너뜀
                List<InlineKeyboardButton> rowInline = new ArrayList<>();
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(phone.model());  // 모델명을 버튼 텍스트로 사용
                button.setCallbackData(phone.model());  // 모델명을 콜백 데이터로 사용
                rowInline.add(button);
                rowsInline.add(rowInline);
                addedModels.add(phone.model());  // 모델 추가됨을 기록
            }
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
        List<PhoneDto> phones = phoneService.getPhonesByModel(model);  // 모델에 해당하는 모든 폰을 조회
        if (!phones.isEmpty()) {
            for (PhoneDto phone : phones) {  // 선택한 모델의 모든 폰을 표시
                // 이미지가 있을 경우 이미지를 전송
                if (phone.imagePath() != null && !phone.imagePath().toString().isEmpty()) {
                    String caption = String.format(
                            "\uD83D\uDCF1 모델: %s\n" +
                                    "💵 가격: %s$\n" +
                                    "📝 상태: %s\n",
                            phone.model(), phone.price(), phone.condition()
                    );
                    sendPhoto(chatId, phone.imagePath(), caption);  // 이미지와 캡션을 함께 전송
                } else {
                    sendMessage(chatId, "이미지가 없습니다.");
                }
            }
        } else {
            sendMessage(chatId, "선택한 모델의 정보를 찾을 수 없습니다.");
        }
    }

    private void sendPhoto(Long chatId, String imagePath, String caption) {
        SendPhoto sendPhotoRequest = new SendPhoto();
        sendPhotoRequest.setChatId(chatId.toString());
        sendPhotoRequest.setPhoto(new InputFile(new File(imagePath)));
        sendPhotoRequest.setCaption(caption);

        // 현재 사용자 권한 확인
        Optional<UserDto> user = userService.getUserByTelegramId(chatId);
        boolean isAdmin = user.isPresent() && user.get().role() == Role.ADMIN;

        if (isAdmin) {
            // 관리자에게만 수정 및 삭제 버튼 추가
            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("수정");
            editButton.setCallbackData("edit_phone");

            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("삭제");
            deleteButton.setCallbackData("delete_phone");

            List<InlineKeyboardButton> rowInline = Arrays.asList(editButton, deleteButton);
            rowsInline.add(rowInline);

            inlineKeyboard.setKeyboard(rowsInline);
            sendPhotoRequest.setReplyMarkup(inlineKeyboard);
        }

        try {
            execute(sendPhotoRequest);  // 사진 전송
        } catch (TelegramApiException e) {
            e.printStackTrace();
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