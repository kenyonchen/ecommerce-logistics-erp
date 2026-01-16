package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import lombok.Getter;

import java.util.Objects;

/**
 * 物流标签值对象，包含物流信息的标签，用于贴在包裹上。
 *
 * @author kenyon
 */
@Getter
public class LogisticsLabel {
    private final String labelId;
    private final String trackingNumber;
    private final ShipperInfo shipperInfo;
    private final RecipientInfo recipientInfo;
    private final PackageInfo packageInfo;
    private final String barcode;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param labelId        标签ID
     * @param trackingNumber 运单号
     * @param shipperInfo    发货人信息
     * @param recipientInfo  收件人信息
     * @param packageInfo    包裹信息
     * @param barcode        条形码
     */
    private LogisticsLabel(String labelId, String trackingNumber, ShipperInfo shipperInfo, RecipientInfo recipientInfo, PackageInfo packageInfo, String barcode) {
        this.labelId = labelId;
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "Tracking number cannot be null");
        this.shipperInfo = Objects.requireNonNull(shipperInfo, "Shipper info cannot be null");
        this.recipientInfo = Objects.requireNonNull(recipientInfo, "Recipient info cannot be null");
        this.packageInfo = Objects.requireNonNull(packageInfo, "Package info cannot be null");
        this.barcode = barcode;
    }

    // Builder class
    public static Builder builder() {
        return new Builder();
    }

    // Getter methods
    public String getLabelId() {
        return labelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipperInfo getShipperInfo() {
        return shipperInfo;
    }

    public RecipientInfo getRecipientInfo() {
        return recipientInfo;
    }

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public String getBarcode() {
        return barcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogisticsLabel that = (LogisticsLabel) o;
        return Objects.equals(labelId, that.labelId) &&
                Objects.equals(trackingNumber, that.trackingNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelId, trackingNumber);
    }

    public static class Builder {
        private String labelId;
        private String trackingNumber;
        private ShipperInfo shipperInfo;
        private RecipientInfo recipientInfo;
        private PackageInfo packageInfo;
        private String barcode;

        public Builder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder shipperInfo(ShipperInfo shipperInfo) {
            this.shipperInfo = shipperInfo;
            return this;
        }

        public Builder recipientInfo(RecipientInfo recipientInfo) {
            this.recipientInfo = recipientInfo;
            return this;
        }

        public Builder packageInfo(PackageInfo packageInfo) {
            this.packageInfo = packageInfo;
            return this;
        }

        public Builder barcode(String barcode) {
            this.barcode = barcode;
            return this;
        }

        public LogisticsLabel build() {
            return new LogisticsLabel(labelId, trackingNumber, shipperInfo, recipientInfo, packageInfo, barcode);
        }

        public Builder toBuilder() {
            return this;
        }
    }

    /**
     * 发货人信息值对象。
     */
    @Getter
    public static class ShipperInfo {
        private final String name;
        private final String address;
        private final String contact;
        private final String phone;
        private final String email;

        private ShipperInfo(String name, String address, String contact, String phone, String email) {
            this.name = Objects.requireNonNull(name, "Shipper name cannot be null");
            this.address = Objects.requireNonNull(address, "Shipper address cannot be null");
            this.contact = contact;
            this.phone = Objects.requireNonNull(phone, "Shipper phone cannot be null");
            this.email = email;
        }

        // Builder class
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String name;
            private String address;
            private String contact;
            private String phone;
            private String email;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder address(String address) {
                this.address = address;
                return this;
            }

            public Builder contact(String contact) {
                this.contact = contact;
                return this;
            }

            public Builder phone(String phone) {
                this.phone = phone;
                return this;
            }

            public Builder email(String email) {
                this.email = email;
                return this;
            }

            public ShipperInfo build() {
                return new ShipperInfo(name, address, contact, phone, email);
            }

            public Builder toBuilder() {
                return this;
            }
        }
    }

    /**
     * 收件人信息值对象。
     */
    @Getter
    public static class RecipientInfo {
        private final String name;
        private final String address;
        private final String contact;
        private final String phone;
        private final String email;

        private RecipientInfo(String name, String address, String contact, String phone, String email) {
            this.name = Objects.requireNonNull(name, "Recipient name cannot be null");
            this.address = Objects.requireNonNull(address, "Recipient address cannot be null");
            this.contact = contact;
            this.phone = Objects.requireNonNull(phone, "Recipient phone cannot be null");
            this.email = email;
        }

        // Builder class
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String name;
            private String address;
            private String contact;
            private String phone;
            private String email;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder address(String address) {
                this.address = address;
                return this;
            }

            public Builder contact(String contact) {
                this.contact = contact;
                return this;
            }

            public Builder phone(String phone) {
                this.phone = phone;
                return this;
            }

            public Builder email(String email) {
                this.email = email;
                return this;
            }

            public RecipientInfo build() {
                return new RecipientInfo(name, address, contact, phone, email);
            }

            public Builder toBuilder() {
                return this;
            }
        }
    }

    /**
     * 包裹信息值对象。
     */
    @Getter
    public static class PackageInfo {
        private final double weight;
        private final double length;
        private final double width;
        private final double height;
        private final String weightUnit;
        private final String dimensionUnit;
        private final int quantity;

        private PackageInfo(double weight, double length, double width, double height, String weightUnit, String dimensionUnit, int quantity) {
            this.weight = weight > 0 ? weight : 0.1;
            this.length = length > 0 ? length : 1;
            this.width = width > 0 ? width : 1;
            this.height = height > 0 ? height : 1;
            this.weightUnit = weightUnit != null ? weightUnit : "kg";
            this.dimensionUnit = dimensionUnit != null ? dimensionUnit : "cm";
            this.quantity = quantity > 0 ? quantity : 1;
        }

        // Builder class
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private double weight;
            private double length;
            private double width;
            private double height;
            private String weightUnit;
            private String dimensionUnit;
            private int quantity;

            public Builder weight(double weight) {
                this.weight = weight;
                return this;
            }

            public Builder length(double length) {
                this.length = length;
                return this;
            }

            public Builder width(double width) {
                this.width = width;
                return this;
            }

            public Builder height(double height) {
                this.height = height;
                return this;
            }

            public Builder weightUnit(String weightUnit) {
                this.weightUnit = weightUnit;
                return this;
            }

            public Builder dimensionUnit(String dimensionUnit) {
                this.dimensionUnit = dimensionUnit;
                return this;
            }

            public Builder quantity(int quantity) {
                this.quantity = quantity;
                return this;
            }

            public PackageInfo build() {
                return new PackageInfo(weight, length, width, height, weightUnit, dimensionUnit, quantity);
            }

            public Builder toBuilder() {
                return this;
            }
        }
    }
}