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

    private static final String ADDING_BRAND = "ADDING_BRAND";
    private static final String ADDING_MODEL = "ADDING_MODEL";
    private static final String ADDING_PRICE = "ADDING_PRICE";
    private static final String ADDING_CONDITION = "ADDING_CONDITION";
    private static final String ADDING_PHOTO = "ADDING_PHOTO";

    private static final String EDITING_BRAND = "EDITING_BRAND";
    private static final String EDITING_MODEL = "EDITING_MODEL";
    private static final String EDITING_PRICE = "EDITING_PRICE";
    private static final String EDITING_CONDITION = "EDITING_CONDITION";
    private static final String EDITING_PHOTO = "EDITING_PHOTO";

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
            } else if ("order_phone_".equals(callbackData)) {
                // URL을 포함한 메시지를 생성
                String channelUrl = "https://t.me/Husanboy1995hakimov19";
                String messageText = "yanada ko'proq ma'lumotlar uchum admin bilan ulaning.";

                // 인라인 키보드 버튼 생성
                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                InlineKeyboardButton channelButton = new InlineKeyboardButton();
                channelButton.setText("\uD83D\uDC64 admin bilan aloqa");
                channelButton.setUrl(channelUrl);  // 채널 링크 설정

                List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
                keyboardButtonsRow.add(channelButton);

                List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
                rowList.add(keyboardButtonsRow);

                inlineKeyboardMarkup.setKeyboard(rowList);

                // 메시지를 전송
                SendMessage message = new SendMessage();
                message.setChatId(callbackChatId);
                message.setText(messageText);
                message.setReplyMarkup(inlineKeyboardMarkup);

                try {
                    execute(message);  // 메시지를 Telegram API를 통해 전송
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // handleAddPhoneCommand와 handleEditPhoneCommand를 통해 각각의 상태 진입
    private void handleAddPhoneCommand(Long chatId) {
        phoneDataBuffer.put(chatId, new PhoneEntity()); // 새로운 휴대폰 엔티티를 생성
        userState.put(chatId, "ADDING_BRAND");
        sendMessage(chatId, "Qo‘shiladigan telefon brendini kiriting:");
        sendMessage(chatId, "Masalan: SAMSUNG, IPHONE, OTHER");
    }

    private void verifyAddAdmin(Long chatId, String inputPassword, String userName) {
        if(adminPassword.equals(inputPassword)) {
            try {
                // 관리자 권한 부여 로직 호출
                userService.addAdmin(chatId, userName);
                sendMessage(chatId, "Tabriklaymiz! " + userName + " administrator sifatida qo'shildi.");
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, e.getMessage());
            }
        } else {
            sendMessage(chatId, "Parol noto'g'ri. Iltimos, qayta urinib ko'ring.");
        }
        userState.remove(chatId); // 상태 초기화
    }

    private void verifyRemoveAdmin(Long chatId, String inputPassword, String userName) {
        if (adminPassword.equals(inputPassword)) {
            try {
                userService.removeAdmin(chatId);
                sendMessage(chatId, "Administrator " + userName + " muvaffaqiyatli o'chirildi!");
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, e.getMessage());
            }
        } else {
            sendMessage(chatId, "Parol noto'g'ri. Iltimos, qayta urinib ko'ring.");
        }
        userState.remove(chatId);
    }

    // handleMessage 메서드: 추가와 수정 상태를 구분해 처리
    private void handleMessage(Long chatId, String messageText, Long telegramId) {
        Optional<UserDto> user = userService.getUserByTelegramId(telegramId);
        String state = userState.getOrDefault(chatId, "");

        if (user.isPresent() && user.get().role() == Role.ADMIN) {
            switch (state) {
                // 휴대폰 추가 단계
                case ADDING_BRAND -> {
                    phoneDataBuffer.get(chatId).setBrand(messageText);
                    userState.put(chatId, ADDING_MODEL);
                    sendMessage(chatId, "Qo'shmoqchi bo'lgan model nomini kiriting:");
                    sendMessage(chatId, "Masalan: 12pro, A23..");
                }
                case ADDING_MODEL -> {
                    phoneDataBuffer.get(chatId).setModel(messageText);
                    userState.put(chatId, ADDING_PRICE);
                    sendMessage(chatId, "Qo'shmoqchi bo'lgan narxni kiriting:");
                }
                case ADDING_PRICE -> {
                    try {
                        phoneDataBuffer.get(chatId).setPrice(Double.parseDouble(messageText));
                        userState.put(chatId, ADDING_CONDITION);
                        sendMessage(chatId, "Qo'shmoqchi bo'lgan holatni kiriting (masalan: yangi, ishlatilgan):");
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Iltimos, to'g'ri raqam kiriting.");
                    }
                }
                case ADDING_CONDITION -> {
                    phoneDataBuffer.get(chatId).setCondition(messageText);
                    userState.put(chatId, ADDING_PHOTO);
                    sendMessage(chatId, "Telefonning fotosuratini yuklang:");
                }

                // 휴대폰 수정 단계
                case EDITING_BRAND -> {
                    phoneDataBuffer.get(chatId).setBrand(messageText);
                    userState.put(chatId, EDITING_MODEL);
                    sendMessage(chatId, "O'zgartirmoqchi bo'lgan model nomini kiriting:");
                }
                case EDITING_MODEL -> {
                    phoneDataBuffer.get(chatId).setModel(messageText);
                    userState.put(chatId, EDITING_PRICE);
                    sendMessage(chatId, "O'zgartirmoqchi bo'lgan narxni kiriting:");
                }
                case EDITING_PRICE -> {
                    try {
                        phoneDataBuffer.get(chatId).setPrice(Double.parseDouble(messageText));
                        userState.put(chatId, EDITING_CONDITION);
                        sendMessage(chatId, "O'zgartirmoqchi bo'lgan holatni kiriting (masalan: yangi, ishlatilgan):");
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Iltimos, to'g'ri raqam kiriting.");
                    }
                }
                case EDITING_CONDITION -> {
                    phoneDataBuffer.get(chatId).setCondition(messageText);
                    userState.put(chatId, EDITING_PHOTO);
                    sendMessage(chatId, "Yangilangan telefon fotosuratini yuklang:\"");
                }
                default -> {
                    if ("/add_phone".equals(messageText)) {
                        handleAddPhoneCommand(chatId);
                    }
                }
            }
        } else if(user.isPresent() && user.get().role() == Role.USER && messageText.startsWith("/add_phone")){
            sendMessage(chatId, "Bu buyruqni faqat Admin amalga oshira oladi!!");
        }
    }

    // handlePhotoUpload 메서드: 추가 및 수정 상태에 따라 처리
    private void handlePhotoUpload(Long chatId, Update update) {
        String state = userState.getOrDefault(chatId, "");

        if (ADDING_PHOTO.equals(state) || EDITING_PHOTO.equals(state)) {
            try {
                String fileId = update.getMessage().getPhoto().stream()
                        .max(Comparator.comparing(photoSize -> photoSize.getFileSize()))
                        .get().getFileId();
                String filePath = getFilePath(fileId);
                File imageFile = downloadAndCompressImage(filePath);

                PhoneEntity phoneEntity = phoneDataBuffer.get(chatId);

                if (ADDING_PHOTO.equals(state)) {
                    phoneService.addPhone(PhoneDto.toDto(phoneEntity), imageFile);
                    sendMessage(chatId, "Yangi telefon muvaffaqiyatli qo'shildi!");
                } else {
                    phoneService.updatePhone(phoneEntity.getId(), PhoneDto.toDto(phoneEntity), imageFile);
                    sendMessage(chatId, "Telefon ma'lumotlari muvaffaqiyatli o'zgartirildi!");
                }

                phoneDataBuffer.remove(chatId);
                userState.remove(chatId);

            } catch (Exception e) {
                e.printStackTrace();
                sendMessage(chatId, "Rasmni qayta ishlashda xatolik yuz berdi.");
            }
        }
    }

    private void handleEditPhoneCommand(Long chatId) {
        Optional<PhoneEntity> latestPhone = phoneService.getLatestPhone(); // 가장 최근에 추가된 휴대폰 조회
        if (latestPhone.isPresent()) {
            phoneDataBuffer.put(chatId, latestPhone.get());
            userState.put(chatId, "EDITING_BRAND");
            sendMessage(chatId, "O'zgartirish uchun brend nomini kiriting:");
        } else {
            sendMessage(chatId, "O'zgartirish uchun telefon mavjud emas.");
        }
    }

    private void handleDeletePhoneCommand(Long chatId) {
        Optional<PhoneEntity> latestPhone = phoneService.getLatestPhone();  // 가장 최근에 추가된 휴대폰 조회
        if (latestPhone.isPresent()) {
            phoneService.deletePhone(latestPhone.get().getId());
            sendMessage(chatId, "Telefon muvaffaqiyatli o'chirildi.");
        } else {
            sendMessage(chatId, "O'chirish uchun telefon mavjud emas.");
        }
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
        sendMessage(chatId, "Admin parolini kiriting:");
    }

    // remove admin method
    private void handleRemoveAdminCommand(Long chatId, String messageText) {
        userState.put(chatId, WAITING_FOR_REMOVE_ADMIN);
        sendMessage(chatId, "Admin parolini kiriting:");
    }


    private void handleStartCommand(Optional<UserDto> existingUser, Long chatId, String userName, Long telegramId) {
        if(existingUser.isPresent()) {
            sendMessage(chatId, "Qaytib kelganingizdan xursandmiz, " + userName + "! \uD83D\uDC4B");
        } else {
            userService.addUser(telegramId, userName, Role.USER);
            sendMessage(chatId, "Assalomalekum, " + userName + "! Botimizga xush kelibsiz. \uD83D\uDC4B");
        }
        showMenuButtons(chatId);
    }

    private void handlePhoneBrandSelection(Long chatId, String brand) {
        List<PhoneDto> phones = phoneService.getPhonesByBrand(brand);
        if(phones.isEmpty()) {
            sendMessage(chatId, "Tanlangan brend uchun telefon mavjud emas.");
        } else {
            sendModelButtons(chatId, phones);  // 모델 버튼 표시
        }
    }

    private void sendModelButtons(Long chatId, List<PhoneDto> phones) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Modelni tanlang:");

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
                            "\uD83D\uDCF1 Model: %s\n" +
                                    "💵 Narxi: %s$\n" +
                                    "📝 Holati: %s\n",
                            phone.model(), phone.price(), phone.condition()
                    );
                    sendPhoto(chatId, phone.imagePath(), caption);  // 이미지와 캡션을 함께 전송
                } else {
                    sendMessage(chatId, "Rasm mavjud emas.");
                }
            }
        }
    }

    private void sendPhoto(Long chatId, String imagePath, String caption) {
        SendPhoto sendPhotoRequest = new SendPhoto();
        sendPhotoRequest.setChatId(chatId.toString());
        sendPhotoRequest.setPhoto(new InputFile(new File(imagePath)));
        sendPhotoRequest.setCaption(caption);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        InlineKeyboardButton orderButton = new InlineKeyboardButton();
        orderButton.setText("🛒  Buyurtma berish  🛒");
        orderButton.setCallbackData("order_phone_");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(orderButton);
        keyboard.add(row);

        inlineKeyboardMarkup.setKeyboard(keyboard);
        sendPhotoRequest.setReplyMarkup(inlineKeyboardMarkup);

        // 현재 사용자 권한 확인
        Optional<UserDto> user = userService.getUserByTelegramId(chatId);
        boolean isAdmin = user.isPresent() && user.get().role() == Role.ADMIN;

        if (isAdmin) {
            // 관리자에게만 수정 및 삭제 버튼 추가
            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("O'zgartirish");
            editButton.setCallbackData("edit_phone");

            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("O'chirish");
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
        message.setText("Iltimos siz xoxlagan telefon brendini tanland!:");
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