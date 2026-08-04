package org.example.gp.entity;

/**
 * Статус на сканиран документ.
 * REVIEWED/EXPORTED са подготвени за бъдещ workflow (OCR преглед + експорт
 * към Микроинвест Делта Про), но за момента се ползва основно NEW.
 */
public enum DocumentStatus {
    NEW,
    REVIEWED,
    EXPORTED
}
