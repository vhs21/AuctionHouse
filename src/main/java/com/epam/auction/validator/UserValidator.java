package com.epam.auction.validator;

import com.epam.auction.entity.User;

public class UserValidator extends Validator {

    private static final String USERNAME_PATTERN = "[A-Za-z][A-Za-z0-9.\\-]{4,20}";
    private static final String PASSWORD_PATTERN = "(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{6,30}";
    private static final String NAME_PATTERN = "[A-Za-zА-Яа-яЁё]{2,45}";
    private static final String EMAIL_PATTERN = "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,3}$";
    private static final String PHONE_PATTERN = "[+][0-9]{11,12}";

    public boolean validateSignUpParam(User user) {
        return validate(user.getUsername(), USERNAME_PATTERN) &&
                validate(user.getPassword(), PASSWORD_PATTERN) &&
                validate(user.getLastName(), NAME_PATTERN) &&
                validateMiddleName(user.getMiddleName()) &&
                validate(user.getFirstName(), NAME_PATTERN) &&
                validate(user.getEmail(), EMAIL_PATTERN) &&
                validate(user.getPhoneNumber(), PHONE_PATTERN);
    }

    public boolean validateUsername(String username) {
        return validate(username, USERNAME_PATTERN);
    }

    public boolean validateEmail(String email) {
        return validate(email, EMAIL_PATTERN);
    }

    public boolean validatePassword(String password) {
        return validate(password, PASSWORD_PATTERN);
    }

    public boolean validateProfile(String lastName, String middleName, String firstName, String phoneNumber) {
        return validate(lastName, NAME_PATTERN) &&
                validateMiddleName(middleName) &&
                validate(firstName, NAME_PATTERN) &&
                validate(phoneNumber, PHONE_PATTERN);
    }

    private boolean validateMiddleName(String middleName) {
        return middleName == null || middleName.isEmpty() || validate(middleName, NAME_PATTERN);
    }

}