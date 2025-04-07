package hunre.edu.vn.backend.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import hunre.edu.vn.backend.entity.Order;
import hunre.edu.vn.backend.entity.OrderDetail;
import hunre.edu.vn.backend.entity.User;
import hunre.edu.vn.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class InvoiceService {
    @Autowired
    private OrderRepository orderRepository;

    public byte[] generateInvoicePdf(Long orderId) throws Exception {
        // Lấy thông tin đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Tạo PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);

        // Sử dụng font mặc định
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        // Thông tin công ty
        document.add(new Paragraph("THAPV Pharmacity")
                .setFont(boldFont)
                .setTextAlignment(TextAlignment.LEFT)
                .setFontSize(12));
        document.add(new Paragraph("Số 285, Đội Cấn, Ba Đình, Hà Nội")
                .setFont(regularFont)
                .setTextAlignment(TextAlignment.LEFT)
                .setFontSize(10));

        // Tiêu đề hóa đơn
        document.add(new Paragraph("HÓA ĐƠN")
                .setFont(boldFont)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16)
                .setMarginTop(20)
                .setMarginBottom(20));

        // Thông tin khách hàng
        User user = order.getPatient().getUser();
        document.add(new Paragraph("Khách hàng: " + user.getFullName())
                .setFont(regularFont)
                .setFontSize(10));
        document.add(new Paragraph("Email: " + user.getEmail())
                .setFont(regularFont)
                .setFontSize(10));

        // Thông tin đơn hàng
        document.add(new Paragraph("Mã đơn hàng: " + order.getOrderCode())
                .setFont(regularFont)
                .setFontSize(10));
        document.add(new Paragraph("Ngày: " +
                order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .setFont(regularFont)
                .setFontSize(10));

        // Tạo bảng chi tiết
        float[] columnWidths = {2, 1, 2, 2};
        Table table = new Table(columnWidths).useAllAvailableWidth();

        // Header bảng
        String[] headers = {"Sản phẩm", "Số lượng", "Đơn giá", "Thành tiền"};
        for (String header : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(boldFont))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setFontSize(10));
        }

        // Dữ liệu sản phẩm
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderDetail detail : order.getOrderDetails()) {
            BigDecimal lineTotal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detail.getQuantity()));

            // Sản phẩm
            table.addCell(new Cell()
                    .add(new Paragraph(detail.getMedicine().getName())
                            .setFont(regularFont)
                            .setFontSize(10)));

            // Số lượng
            table.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(detail.getQuantity()))
                            .setFont(regularFont)
                            .setFontSize(10)));

            // Đơn giá
            table.addCell(new Cell()
                    .add(new Paragraph(formatCurrency(detail.getUnitPrice()))
                            .setFont(regularFont)
                            .setFontSize(10)));

            // Thành tiền
            table.addCell(new Cell()
                    .add(new Paragraph(formatCurrency(lineTotal))
                            .setFont(regularFont)
                            .setFontSize(10)));

            totalAmount = totalAmount.add(lineTotal);
        }

        document.add(table);

        // Tổng cộng
        document.add(new Paragraph("Tổng cộng: " + formatCurrency(totalAmount))
                .setFont(boldFont)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(12)
                .setMarginTop(10));

        // Chữ ký
        document.add(new Paragraph("AN")
                .setFont(boldFont)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(20)
                .setMarginTop(20));

        document.close();

        return baos.toByteArray();
    }

    // Định dạng tiền tệ
    private String formatCurrency(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return formatter.format(amount);
    }
}