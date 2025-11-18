import com.yeab.esnapp.databinding.ItemProductStatusBinding
import com.yeab.esnapp.model.ProductStatus

class ProductStatusAdapter :
    androidx.recyclerview.widget.RecyclerView.Adapter<ProductStatusAdapter.StatusViewHolder>() {

    private val items = mutableListOf<ProductStatus>()

    fun submitList(list: List<ProductStatus>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class StatusViewHolder(val binding: ItemProductStatusBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StatusViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        val binding = ItemProductStatusBinding.inflate(inflater, parent, false)
        return StatusViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val item = items[position]
        holder.binding.txtStatus.text = item.status
        holder.binding.txtDate.text = item.date
    }
}
