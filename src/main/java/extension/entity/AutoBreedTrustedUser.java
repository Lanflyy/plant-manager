package extension.entity;

import gearth.extensions.parsers.HEntity;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AutoBreedTrustedUser {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty userId = new SimpleStringProperty("(not loaded)");
    private volatile HEntity entity;

    public AutoBreedTrustedUser(String username) {
        this.username.set(username);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public StringProperty userIdProperty() {
        return userId;
    }

    public String getUserId() {
        return userId.get();
    }

    public HEntity getEntity() {
        return entity;
    }

    public void setEntity(HEntity entity) {
        this.entity = entity;
        this.userId.set(entity == null ? "(not loaded)" : entity.getId() + " (loaded)");
    }
}

