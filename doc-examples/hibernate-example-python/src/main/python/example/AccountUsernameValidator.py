import re

from jakarta.inject import Singleton
from jakarta.persistence import PrePersist

from example.Account import Account


@Singleton
class AccountUsernameValidator:

    @PrePersist
    def validate_username(self, account: Account) -> None:
        username = account.username
        if username is None or not re.fullmatch("[a-z0-9]+", username):
            raise ValueError("Invalid username")
