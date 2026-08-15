package com.payments.platform.paymentservice.outbox;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

@Converter(autoApply = false)
public class JsonbStringConverter implements AttributeConverter<String, PGobject> {

    @Override
    public PGobject convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(attribute);
            return pg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert String to PGobject(jsonb)", e);
        }
    }

    @Override
    public String convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) return null;
        try {
            return dbData.getValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert PGobject(jsonb) to String", e);
        }
    }
}
